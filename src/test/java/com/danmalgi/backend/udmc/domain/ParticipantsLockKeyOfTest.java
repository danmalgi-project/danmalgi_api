package com.danmalgi.backend.udmc.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class ParticipantsLockKeyOfTest {

    @Test
    void of_참여자_순서가_달라도_같은_키를_반환한다() {
        assertThat(ParticipantsLockKey.of(List.of(1L, 2L)))
                .isEqualTo(ParticipantsLockKey.of(List.of(2L, 1L)));
    }

    @Test
    void of_참여자가_다르면_다른_키를_반환한다() {
        assertThat(ParticipantsLockKey.of(List.of(1L, 2L)))
                .isNotEqualTo(ParticipantsLockKey.of(List.of(1L, 3L)));
    }

    @Test
    void of_같은_입력은_항상_같은_키를_반환한다() {
        long first = ParticipantsLockKey.of(List.of(7L, 42L));
        long second = ParticipantsLockKey.of(List.of(7L, 42L));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void of_64비트_전_구간을_사용한다() {
        // 32bit 해시(String.hashCode 등)를 그대로 쓰면 상위 32비트가 항상 0 이라 실패한다.
        boolean usesHighBits = LongStream.rangeClosed(2L, 101L)
                .anyMatch(friendId -> (ParticipantsLockKey.of(List.of(1L, friendId)) >>> 32) != 0L);

        assertThat(usesHighBits).isTrue();
    }

    @Test
    void of_null이면_IllegalArgumentException() {
        assertThatThrownBy(() -> ParticipantsLockKey.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participantIds");
    }

    @Test
    void of_빈_목록이면_IllegalArgumentException() {
        assertThatThrownBy(() -> ParticipantsLockKey.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participantIds");
    }
}
