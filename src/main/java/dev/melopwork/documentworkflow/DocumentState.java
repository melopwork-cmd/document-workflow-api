package dev.melopwork.documentworkflow;

import java.util.List;

public enum DocumentState {
    RECEIVED, IN_REVIEW, APPROVED, REJECTED, ARCHIVED;

    public boolean allows(DocumentState next) {
        return next != null && availableTransitions().contains(next);
    }

    public List<DocumentState> availableTransitions() {
        return switch (this) {
            case RECEIVED -> List.of(IN_REVIEW);
            case IN_REVIEW -> List.of(APPROVED, REJECTED);
            case APPROVED, REJECTED -> List.of(ARCHIVED);
            case ARCHIVED -> List.of();
        };
    }
}
