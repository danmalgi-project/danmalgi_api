package com.danmalgi.backend.relation.service;

import com.danmalgi.backend.friendship.repository.persistence.FriendshipJpaRepository;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.relation.domain.exception.RelationUserNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationServiceAddRelationshipTest {

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
    void addRelationship_requester가_없으면_예외발생() {
        when(userJpaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationService.addRelationship(1L, "Bob", "00002"))
                .isInstanceOf(RelationUserNotFoundException.class);
    }

    @Test
    void addRelationship_receiver가_없으면_예외발생() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");

        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userJpaRepository.findByNameAndTag("Bob", "00002")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationService.addRelationship(1L, "Bob", "00002"))
                .isInstanceOf(RelationUserNotFoundException.class);
    }

    @Test
    void addRelationship_자기자신에게_요청하면_예외발생() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");

        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userJpaRepository.findByNameAndTag("Alice", "00001")).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> relationService.addRelationship(1L, "Alice", "00001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void addRelationship_정상_요청이면_RelationEntity_저장_후_목록_반환() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");

        RelationEntity savedRelation = mock(RelationEntity.class);
        when(savedRelation.getId()).thenReturn(1L);
        when(savedRelation.getReceiver()).thenReturn(receiver);
        when(savedRelation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());

        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userJpaRepository.findByNameAndTag("Bob", "00002")).thenReturn(Optional.of(receiver));
        when(relationJpaRepository.save(any())).thenReturn(savedRelation);
        when(relationJpaRepository.findAllByRequesterId(1L)).thenReturn(List.of(savedRelation));

        List<Relation> result = relationService.addRelationship(1L, "Bob", "00002");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(RelationStatus.PENDING.forNumber());
        verify(relationJpaRepository).save(any());
    }

    @Test
    void addRelationship_receiver의_profileImageUrl이_path이면_public_URL로_치환된다() {
        UserEntity requester = UserFixture.createUserEntity(1L, "Alice", "00001");
        UserEntity receiver = UserFixture.createUserEntity(2L, "Bob", "00002");
        receiver.updateProfileImageUrl("profiles/2/img.webp");

        RelationEntity savedRelation = mock(RelationEntity.class);
        when(savedRelation.getId()).thenReturn(1L);
        when(savedRelation.getReceiver()).thenReturn(receiver);
        when(savedRelation.getStatus()).thenReturn(RelationStatus.PENDING.forNumber());

        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userJpaRepository.findByNameAndTag("Bob", "00002")).thenReturn(Optional.of(receiver));
        when(relationJpaRepository.save(any())).thenReturn(savedRelation);
        when(relationJpaRepository.findAllByRequesterId(1L)).thenReturn(List.of(savedRelation));
        when(r2Uploader.toPublicUrl("profiles/2/img.webp"))
                .thenReturn("https://cdn.example.com/profiles/2/img.webp");

        List<Relation> result = relationService.addRelationship(1L, "Bob", "00002");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profiles/2/img.webp");
        verify(r2Uploader).toPublicUrl("profiles/2/img.webp");
    }
}
