package com.danmalgi.backend.relation.domain.model;

import com.danmalgi.backend.user.domain.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class Relation {
    private Long id;
    private User user;
    private int status;
}
