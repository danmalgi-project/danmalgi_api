package com.danmalgi.backend.store.dm_store.grpc;

import com.danmalgi.backend.store.dm_store.grpc.validator.DmStoreGrpcValidator;
import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.GetChannelNamesRequest;
import com.danmalgi.backend.internal.dm_store.v1.DmStoreProto.GetChannelNamesResponse;
import com.danmalgi.backend.udmc.service.UserDirectMessageChannelService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DmStoreGrpcGetChannelNamesTest {

    @Mock
    private UserDirectMessageChannelService userDirectMessageChannelService;
    @Mock
    private DmStoreGrpcValidator validator;
    @Mock
    private StreamObserver<GetChannelNamesResponse> responseObserver;

    private DmStoreGrpc dmStoreGrpc;

    @BeforeEach
    void setUp() {
        dmStoreGrpc = new DmStoreGrpc(userDirectMessageChannelService, validator);
    }

    @Test
    void getChannelNames_요청으로_채널명_목록을_응답한다() {
        GetChannelNamesRequest request = GetChannelNamesRequest.newBuilder()
                .setChannelId(1L)
                .addAllUserIds(List.of(10L, 20L))
                .build();

        when(userDirectMessageChannelService.getChannelNames(1L, List.of(10L, 20L)))
                .thenReturn(Map.of(10L, "Bob", 20L, "Alice"));

        dmStoreGrpc.getChannelNames(request, responseObserver);

        ArgumentCaptor<GetChannelNamesResponse> captor = ArgumentCaptor.forClass(GetChannelNamesResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertThat(captor.getValue().getChannelNamesList()).hasSize(2);
    }

    @Test
    void getChannelNames_validator를_호출한다() {
        GetChannelNamesRequest request = GetChannelNamesRequest.newBuilder()
                .setChannelId(1L)
                .build();

        when(userDirectMessageChannelService.getChannelNames(any(), any()))
                .thenReturn(Map.of());

        dmStoreGrpc.getChannelNames(request, responseObserver);

        verify(validator).validateGetChannelNamesRequest(request);
    }
}
