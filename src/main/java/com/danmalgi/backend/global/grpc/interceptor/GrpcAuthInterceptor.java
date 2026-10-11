package com.danmalgi.backend.global.grpc.interceptor;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;

import com.danmalgi.backend.auth.infrastructure.pending.PendingAuthStore;
import com.danmalgi.backend.external.auth.v1.AuthServiceGrpc;
import com.danmalgi.backend.external.user.v1.UserServiceGrpc;
import com.danmalgi.backend.global.grpc.context.GrpcContext;
import com.danmalgi.backend.global.security.JwtTokenProvider;
import com.danmalgi.backend.global.security.JwtTokenProvider.JwtPayload;
import com.danmalgi.backend.user.domain.exception.UserNotFoundException;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.service.UserService;

import io.grpc.*;

public class GrpcAuthInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_METHOD = AuthServiceGrpc.SERVICE_NAME + "/Authorization";
    private static final String REFLECTION_METHOD = "grpc.reflection.v1.ServerReflection/ServerReflectionInfo";
    private static final String REGISTER_METHOD = AuthServiceGrpc.SERVICE_NAME + "/Register";
    private static final String VERIFY_NAME_AND_TAG_METHOD =
            UserServiceGrpc.SERVICE_NAME + "/VerifyNameAndTag";

    /**
     * {@code users} 행이 아직 없는 pending 세션이 호출할 수 있는 RPC.
     *
     * <p>회원가입 화면이 자기완결적으로 동작할 최소 집합이다. 그 밖의 RPC 는
     * {@code FAILED_PRECONDITION} 으로 거부한다 — 특히 {@code UpsertFcmToken} 은
     * {@code devices.user_id} FK 때문에 유저 행 없이는 저장할 수 없다.
     */
    private static final Set<String> PENDING_ALLOWED_METHODS =
            Set.of(REGISTER_METHOD, VERIFY_NAME_AND_TAG_METHOD);

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PendingAuthStore pendingAuthStore;

    @Value("${auth.interceptor.enable}")
    private boolean profile;

    public GrpcAuthInterceptor(
        JwtTokenProvider jwtTokenProvider,
        UserService userService,
        PendingAuthStore pendingAuthStore
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.pendingAuthStore = pendingAuthStore;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler
    ) {
        String methodName = serverCall.getMethodDescriptor().getFullMethodName();
        if (AUTHORIZATION_METHOD.equals(methodName)) {
            return serverCallHandler.startCall(serverCall, metadata);
        }

        if (profile && REFLECTION_METHOD.equals(methodName)) {
            return serverCallHandler.startCall(serverCall, metadata);
        }

        String authorizationHeader = metadata.get(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return unauthenticated(serverCall, "authorization header is missing");
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return unauthenticated(serverCall, "authorization header must use Bearer token");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            return unauthenticated(serverCall, "Bearer token is missing");
        }

        try {
            JwtPayload payload = jwtTokenProvider.validateAndGetPayload(token);

            // 인터셉터 throw 는 GlobalGrpcExceptionHandler 를 타지 않는다.
            // 아래 경로들은 status 를 직접 닫아 통제한다.
            if (payload.userId() != null) {
                return interceptAsRegisteredUser(payload, token, serverCall, metadata, serverCallHandler);
            }
            return interceptAsPendingSession(payload, methodName, serverCall, metadata, serverCallHandler);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return unauthenticated(serverCall, "invalid jwt token");
        }
    }

    /** 등록 완료 토큰(userId 클레임) 경로 — DB 에 행이 없으면 거부한다. */
    private <ReqT, RespT> ServerCall.Listener<ReqT> interceptAsRegisteredUser(
            JwtPayload payload,
            String token,
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler
    ) {
        User user;
        try {
            user = userService.getUser(payload.userId());
        } catch (UserNotFoundException e) {
            return unauthenticated(serverCall, "user not found for token");
        }

        Context context = Context.current()
                .withValue(GrpcContext.USER_ID, payload.userId())
                .withValue(GrpcContext.DEVICE_ID, payload.deviceId())
                // chat-server 등 다운스트림 gRPC 호출에 클라이언트 원본 토큰을 그대로 전달하기 위해 보관.
                .withValue(GrpcContext.JWT_TOKEN, token)
                .withValue(GrpcContext.USER, user);

        return Contexts.interceptCall(context, serverCall, metadata, serverCallHandler);
    }

    /**
     * pending 토큰(pendingSessionId 클레임) 경로.
     *
     * <p>{@code Register}/{@code VerifyNameAndTag} 가 아니면 Redis 조회 없이 바로 거부한다.
     * {@code USER_ID}/{@code JWT_TOKEN} 은 심지 않는다 — pending 토큰이 chat-server 등
     * 다운스트림으로 새는 경로를 원천 차단한다.
     */
    private <ReqT, RespT> ServerCall.Listener<ReqT> interceptAsPendingSession(
            JwtPayload payload,
            String methodName,
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler
    ) {
        if (!PENDING_ALLOWED_METHODS.contains(methodName)) {
            return failedPrecondition(serverCall, "user registration is not completed");
        }

        String sessionId = payload.pendingSessionId();
        boolean sessionExists = pendingAuthStore.find(sessionId).isPresent()
                || pendingAuthStore.findCompletedUserId(sessionId).isPresent();
        if (!sessionExists) {
            return unauthenticated(serverCall, "pending registration session not found");
        }

        Context context = Context.current()
                .withValue(GrpcContext.PENDING_SESSION_ID, sessionId)
                .withValue(GrpcContext.DEVICE_ID, payload.deviceId());

        return Contexts.interceptCall(context, serverCall, metadata, serverCallHandler);
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> failedPrecondition(
            ServerCall<ReqT, RespT> serverCall,
            String description
    ) {
        serverCall.close(
                Status.FAILED_PRECONDITION.withDescription(description),
                new Metadata()
        );
        return new ServerCall.Listener<>() {};
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> unauthenticated(
            ServerCall<ReqT, RespT> serverCall,
            String description
    ) {
        serverCall.close(
                Status.UNAUTHENTICATED.withDescription(description),
                new Metadata()
        );
        return new ServerCall.Listener<>() {};
    }
}
