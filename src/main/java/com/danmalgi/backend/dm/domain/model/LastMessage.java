package com.danmalgi.backend.dm.domain.model;

import java.time.Instant;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LastMessage {
    private Long messageId;
    private String content;
    private Long senderId;
    private Instant createdAt;
}
