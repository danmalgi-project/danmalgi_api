package com.danmalgi.backend.global.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

@ExtendWith(MockitoExtension.class)
class GrpcInternalAuthInterceptorInterceptCallTest {

    @Mock
    private ServerCall<Object, Object> serverCall;

    @Mock
    private ServerCallHandler<Object, Object> serverCallHandler;

    @Mock
    private ServerCall.Listener<Object> listener;

    private GrpcInternalAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new GrpcInternalAuthInterceptor();
    }

    @Test
    void Authorization_헤더가_없어도_다음_핸들러로_통과시킨다() {
        // given
        Metadata metadata = new Metadata();
        when(serverCallHandler.startCall(same(serverCall), same(metadata))).thenReturn(listener);

        // when
        ServerCall.Listener<Object> result =
                interceptor.interceptCall(serverCall, metadata, serverCallHandler);

        // then
        assertThat(result).isSameAs(listener);
        verify(serverCallHandler).startCall(same(serverCall), same(metadata));
    }

    @Test
    void 호출을_거부하지_않는다() {
        // given
        Metadata metadata = new Metadata();
        when(serverCallHandler.startCall(same(serverCall), same(metadata))).thenReturn(listener);

        // when
        interceptor.interceptCall(serverCall, metadata, serverCallHandler);

        // then
        verify(serverCall, never()).close(any(Status.class), any(Metadata.class));
    }

    @Test
    void 메타데이터를_변형하지_않고_원본을_그대로_전달한다() {
        // given
        Metadata metadata = new Metadata();
        metadata.put(
                Metadata.Key.of("X-Test", Metadata.ASCII_STRING_MARSHALLER),
                "kept");
        when(serverCallHandler.startCall(same(serverCall), same(metadata))).thenReturn(listener);

        // when
        interceptor.interceptCall(serverCall, metadata, serverCallHandler);

        // then
        assertThat(metadata.get(Metadata.Key.of("X-Test", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("kept");
        verify(serverCallHandler).startCall(same(serverCall), same(metadata));
    }

    @Test
    void 메서드_이름을_들여다보지_않는다() {
        // given: getMethodDescriptor() 를 스텁하지 않는다.
        // pass-through 인터셉터가 메서드 이름을 참조하면 NPE 로 실패한다.
        Metadata metadata = new Metadata();
        when(serverCallHandler.startCall(same(serverCall), same(metadata))).thenReturn(listener);

        // when
        interceptor.interceptCall(serverCall, metadata, serverCallHandler);

        // then
        verify(serverCall, never()).getMethodDescriptor();
    }
}
