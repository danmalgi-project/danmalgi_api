package com.danmalgi.backend.chat.client.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.danmalgi.backend.global.grpc.context.GrpcContext;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

class JwtForwardingClientInterceptorTest {

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    @SuppressWarnings("unchecked")
    private Metadata startOutboundCall(String tokenInContext) {
        ClientCall<Object, Object> delegateCall = mock(ClientCall.class);
        Channel channel = mock(Channel.class);
        when(channel.newCall(any(), any())).thenReturn(delegateCall);
        MethodDescriptor<Object, Object> method = mock(MethodDescriptor.class);

        JwtForwardingClientInterceptor interceptor = new JwtForwardingClientInterceptor();

        Context context = tokenInContext == null
                ? Context.current()
                : Context.current().withValue(GrpcContext.JWT_TOKEN, tokenInContext);
        context.run(() -> {
            ClientCall<Object, Object> call =
                    interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
            call.start(mock(ClientCall.Listener.class), new Metadata());
        });

        ArgumentCaptor<Metadata> headers = ArgumentCaptor.forClass(Metadata.class);
        verify(delegateCall).start(any(), headers.capture());
        return headers.getValue();
    }

    @Test
    void 컨텍스트의_JWT를_Bearer_Authorization_헤더로_아웃바운드에_실어보낸다() {
        Metadata headers = startOutboundCall("client-jwt-123");

        assertThat(headers.get(AUTHORIZATION_HEADER)).isEqualTo("Bearer client-jwt-123");
    }

    @Test
    void 컨텍스트에_JWT가_없으면_Authorization_헤더를_붙이지_않는다() {
        Metadata headers = startOutboundCall(null);

        assertThat(headers.get(AUTHORIZATION_HEADER)).isNull();
    }
}
