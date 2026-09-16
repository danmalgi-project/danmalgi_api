package com.danmalgi.backend.friendship.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum FriendStatus {
    ACCEPT(0),
    BLOCK(1),
    DELETE(2);

    private final int number;

    public static FriendStatus getFriendStatus(int id) {
        for (FriendStatus status : FriendStatus.values()) {
            if (status.getNumber() == id) {
                return status;
            }
        }
        return null;
    }
}
