package com.danmalgi.backend.friendship.domain.model;

import com.danmalgi.backend.user.domain.model.User;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class Friendship {
    private Long id;
    private User friend;
    private FriendStatus status;
}
