package com.danmalgi.backend.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum UserStatus {
    PENDING(0),
    ACTIVE(1),
    BLOCKED(2),
    WITHDRAWAL(3);

    private final int number;

    public static UserStatus fromNumber(int number) {
        for (UserStatus userStatus : values()) {
            if (userStatus.number == number) {
                return userStatus;
            }
        }
        return null;
    }
}
