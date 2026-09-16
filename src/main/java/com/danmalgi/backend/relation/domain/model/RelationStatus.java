package com.danmalgi.backend.relation.domain.model;

public enum RelationStatus {
    ACCEPT(0),
    REJECT(1),
    PENDING(2),
    CANCEL(3);

    private final int number;

    RelationStatus(int number) {
        this.number = number;
    }

    public int forNumber() {
        return number;
    }
}
