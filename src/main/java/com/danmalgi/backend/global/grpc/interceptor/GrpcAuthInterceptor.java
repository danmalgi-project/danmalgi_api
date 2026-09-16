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

            User user = findUserOrNull(payload.userId());
            if (user == null) {
                // 인터셉터 throw 는 GlobalGrpcExceptionHandler 를 타지 않는다.
                // 아래 두 경로는 status 를 직접 닫아 통제한다.
                if (pendingAuthStore.find(payload.userId()).isEmpty()) {
                    return unauthenticated(serverCall, "user not found for token");
                }
                if (!PENDING_ALLOWED_METHODS.contains(methodName)) {
                    return failedPrecondition(serverCall, "user registration is not completed");
                }
            }

            Context context = Context.current()
                    .withValue(GrpcContext.USER_ID, payload.userId())
                    .withValue(GrpcContext.DEVICE_ID, payload.deviceId())
                    // chat-server 등 다운스트림 gRPC 호출에 클라이언트 원본 토큰을 그대로 전달하기 위해 보관.
                    .withValue(GrpcContext.JWT_TOKEN, token);
            if (user != null) {
                context = context.withValue(GrpcContext.USER, user);
            }

            return Contexts.interceptCall(context, serverCall, metadata, serverCallHandler);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return unauthenticated(serverCall, "invalid jwt token");
        }
    }

    /**
     * DB 행이 없으면 {@code null}. pending 세션 판정 전에 먼저 호출한다.
     *
     * <p>순서가 중요하다 — ACTIVE 유저(트래픽의 사실상 전부)의 hot path 에 Redis GET 을
     * 추가하지 않기 위해서다. {@code getUser} 는 {@code @Cacheable("user")} 라 대개 캐시 히트다.
     */
    private User findUserOrNull(Long userId) {
        try {
            return userService.getUser(userId);
        } catch (UserNotFoundException e) {
            return null;
        }
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
