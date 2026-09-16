package com.danmalgi.backend.relation.service;

import com.danmalgi.backend.friendship.repository.persistence.FriendshipJpaRepository;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.relation.domain.exception.RelationAccessDeniedException;
import com.danmalgi.backend.relation.domain.exception.RelationNotFoundException;
import com.danmalgi.backend.relation.domain.exception.RelationUpdateNotAllowedException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationServiceUpdateOutgoingRequestTest {

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
    void updateOutgoingRequest_관계가_없으면_예외발생() {
        when(relationJpaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationService.updateOutgoingRequest(1L, 99L, RelationStatus.CANCEL.forNumber()))
                .isInstanceOf(RelationNotFoundException.class);
    }

    @Test
    void updateOutgoingRequest_requester가_다르면_권한_예외발생() {
        UserEntity otherUser = UserFixture.createUserEntity(2L, "Bob", "00002");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getRequester()).thenReturn(otherUser);
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));

        assertThatThrownBy(() -> relationService.updateOutgoingRequest(1L, 1L, RelationStatus.CANCEL.forNumber()))
                .isInstanceOf(RelationAccessDeniedException.class);
    }

    @Test
    void updateOutgoingRequest_PENDING이_아니면_예외발생() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getRequester()).thenReturn(requester);
        when(relation.getStatus()).thenReturn(RelationStatus.ACCEPT.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));

        assertThatThrownBy(() -> relationService.updateOutgoingRequest(1L, 1L, RelationStatus.CANCEL.forNumber()))
                .isInstanceOf(RelationUpdateNotAllowedException.class);
    }

    @Test
    void updateOutgoingRequest_정상_요청이면_상태_업데이트_후_목록_반환() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getRequester()).thenReturn(requester);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));
        when(relationJpaRepository.save(relation)).thenReturn(relation);
        when(relationJpaRepository.findAllByRequesterIdAndStatus(1L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of());

        relationService.updateOutgoingRequest(1L, 1L, RelationStatus.CANCEL.forNumber());

        verify(relation).updateStatus(RelationStatus.CANCEL.forNumber());
        verify(relationJpaRepository).save(relation);
    }

    @Test
    void updateOutgoingRequest_반환목록의_receiver_profileImageUrl이_path이면_public_URL로_치환된다() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");
        UserEntity pendingReceiver = UserFixture.createUserEntity(3L, "Carol", "00003");
        pendingReceiver.updateProfileImageUrl("profiles/3/img.webp");

        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getRequester()).thenReturn(requester);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));
        when(relationJpaRepository.save(relation)).thenReturn(relation);

        RelationEntity pendingRelation = mock(RelationEntity.class);
        when(pendingRelation.getId()).thenReturn(2L);
        when(pendingRelation.getReceiver()).thenReturn(pendingReceiver);
        when(pendingRelation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findAllByRequesterIdAndStatus(1L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of(pendingRelation));
        when(r2Uploader.toPublicUrl("profiles/3/img.webp"))
                .thenReturn("https://cdn.example.com/profiles/3/img.webp");

        List<Relation> result = relationService.updateOutgoingRequest(
                1L, 1L, RelationStatus.CANCEL.forNumber());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/3/img.webp");
        verify(r2Uploader).toPublicUrl("profiles/3/img.webp");
    }
}
