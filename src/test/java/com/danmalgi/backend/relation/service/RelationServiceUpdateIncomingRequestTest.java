package com.danmalgi.backend.relation.service;

import com.danmalgi.backend.friendship.domain.model.FriendStatus;
import com.danmalgi.backend.friendship.repository.entity.FriendshipEntity;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationServiceUpdateIncomingRequestTest {

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
    void updateIncomingRequest_관계가_없으면_예외발생() {
        when(relationJpaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationService.updateIncomingRequest(2L, 99L, RelationStatus.ACCEPT.forNumber()))
                .isInstanceOf(RelationNotFoundException.class);
    }

    @Test
    void updateIncomingRequest_receiver가_다르면_권한_예외발생() {
        UserEntity otherUser = UserFixture.createUserEntity(3L, "Carol", "00003");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getReceiver()).thenReturn(otherUser);
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));

        assertThatThrownBy(() -> relationService.updateIncomingRequest(2L, 1L, RelationStatus.ACCEPT.forNumber()))
                .isInstanceOf(RelationAccessDeniedException.class);
    }

    @Test
    void updateIncomingRequest_PENDING이_아니면_예외발생() {
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getReceiver()).thenReturn(receiver);
        when(relation.getStatus()).thenReturn(RelationStatus.ACCEPT.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));

        assertThatThrownBy(() -> relationService.updateIncomingRequest(2L, 1L, RelationStatus.ACCEPT.forNumber()))
                .isInstanceOf(RelationUpdateNotAllowedException.class);
    }

    @Test
    void updateIncomingRequest_정상_요청이면_상태_업데이트_후_목록_반환() {
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getReceiver()).thenReturn(receiver);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));
        when(relationJpaRepository.save(relation)).thenReturn(relation);
        when(relationJpaRepository.findAllByReceiverIdAndStatus(2L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of());

        relationService.updateIncomingRequest(2L, 1L, RelationStatus.REJECT.forNumber());

        verify(relation).updateStatus(RelationStatus.REJECT.forNumber());
        verify(relationJpaRepository).save(relation);
    }

    @Test
    void updateIncomingRequest_ACCEPT이면_양방향_friendship을_생성한다() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");

        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getRequester()).thenReturn(requester);
        when(relation.getReceiver()).thenReturn(receiver);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));
        when(relationJpaRepository.save(relation)).thenReturn(relation);
        when(relationJpaRepository.findAllByReceiverIdAndStatus(2L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of());

        relationService.updateIncomingRequest(2L, 1L, RelationStatus.ACCEPT.forNumber());

        verify(relation).updateStatus(RelationStatus.ACCEPT.forNumber());
        verify(relationJpaRepository).save(relation);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FriendshipEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(friendshipJpaRepository).saveAll(captor.capture());

        List<FriendshipEntity> saved = captor.getValue();

        FriendshipEntity first = saved.get(0);
        assertThat(first.getUser()).isEqualTo(requester);
        assertThat(first.getFriend()).isEqualTo(receiver);
        assertThat(first.getStatus()).isEqualTo(FriendStatus.ACCEPT.getNumber());

        FriendshipEntity second = saved.get(1);
        assertThat(second.getUser()).isEqualTo(receiver);
        assertThat(second.getFriend()).isEqualTo(requester);
        assertThat(second.getStatus()).isEqualTo(FriendStatus.ACCEPT.getNumber());
    }

    @Test
    void updateIncomingRequest_REJECT이면_friendship을_생성하지_않는다() {
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");
        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getReceiver()).thenReturn(receiver);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));
        when(relationJpaRepository.save(relation)).thenReturn(relation);
        when(relationJpaRepository.findAllByReceiverIdAndStatus(2L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of());

        relationService.updateIncomingRequest(2L, 1L, RelationStatus.REJECT.forNumber());

        verify(friendshipJpaRepository, never()).saveAll(any());
    }

    @Test
    void updateIncomingRequest_반환목록의_requester_profileImageUrl이_path이면_public_URL로_치환된다() {
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");
        UserEntity pendingRequester = UserFixture.createUserEntity(3L, "Carol", "00003");
        pendingRequester.updateProfileImageUrl("profiles/3/img.webp");

        RelationEntity relation = mock(RelationEntity.class);
        when(relation.getReceiver()).thenReturn(receiver);
        when(relation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findById(1L)).thenReturn(Optional.of(relation));
        when(relationJpaRepository.save(relation)).thenReturn(relation);

        RelationEntity pendingRelation = mock(RelationEntity.class);
        when(pendingRelation.getId()).thenReturn(2L);
        when(pendingRelation.getRequester()).thenReturn(pendingRequester);
        when(pendingRelation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());
        when(relationJpaRepository.findAllByReceiverIdAndStatus(2L, RelationStatus.PENDING.forNumber()))
                .thenReturn(List.of(pendingRelation));
        when(r2Uploader.toPublicUrl("profiles/3/img.webp"))
                .thenReturn("https://cdn.example.com/profiles/3/img.webp");

        List<Relation> result = relationService.updateIncomingRequest(
                2L, 1L, RelationStatus.REJECT.forNumber());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/3/img.webp");
        verify(r2Uploader).toPublicUrl("profiles/3/img.webp");
    }
}
