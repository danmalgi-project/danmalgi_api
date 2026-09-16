package com.danmalgi.backend.relation.service;

import com.danmalgi.backend.friendship.repository.persistence.FriendshipJpaRepository;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.relation.domain.model.Relation;
import com.danmalgi.backend.relation.domain.model.RelationStatus;
import com.danmalgi.backend.relation.repository.entity.RelationEntity;
import com.danmalgi.backend.relation.repository.persistence.RelationJpaRepository;
import com.danmalgi.backend.user.fixture.UserFixture;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationServiceGetOutgoingRequestTest {

    @Mock
    private RelationJpaRepository relationJpaRepository;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private FriendshipJpaRepository friendshipJpaRepository;

    @Mock
    private R2Uploader r2Uploader;

    private RelationService relationService;

    @BeforeEach
    void setUp() {
        relationService = new RelationService(
                relationJpaRepository, userJpaRepository, friendshipJpaRepository, r2Uploader);
    }

    @Test
    void getOutgoingRequest_PENDING_상태의_관계_목록_반환() {
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getId()).thenReturn(1L);
        when(relation.getReceiver()).thenReturn(receiver);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());

        when(relationJpaRepository.findAllByRequesterIdAndStatus(1L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of(relation));

        List<Relation> result = relationService.getOutgoingRequest(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(RelationStatus.PENDING.forNumber());
        assertThat(result.get(0).getUser().getName()).isEqualTo("Bob");
    }

    @Test
    void getOutgoingRequest_결과가_없으면_빈_목록_반환() {
        when(relationJpaRepository.findAllByRequesterIdAndStatus(1L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of());

        List<Relation> result = relationService.getOutgoingRequest(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getOutgoingRequest_receiver의_profileImageUrl이_path이면_public_URL로_치환된다() {
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");
        receiver.updateProfileImageUrl("profiles/2/img.webp");

        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getId()).thenReturn(1L);
        when(relation.getReceiver()).thenReturn(receiver);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());

        when(relationJpaRepository.findAllByRequesterIdAndStatus(1L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of(relation));
        when(r2Uploader.toPublicUrl("profiles/2/img.webp"))
                .thenReturn("https://cdn.example.com/profiles/2/img.webp");

        List<Relation> result = relationService.getOutgoingRequest(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/2/img.webp");
        verify(r2Uploader).toPublicUrl("profiles/2/img.webp");
    }
}
