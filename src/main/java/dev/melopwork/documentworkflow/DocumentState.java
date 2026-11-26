package dev.melopwork.documentworkflow;

public enum DocumentState {
    RECEIVED, IN_REVIEW, APPROVED, REJECTED, ARCHIVED;

    public boolean allows(DocumentState next) {
        return switch (this) {
            case RECEIVED -> next == IN_REVIEW;
            case IN_REVIEW -> next == APPROVED || next == REJECTED;
            case APPROVED, REJECTED -> next == ARCHIVED;
            case ARCHIVED -> false;
        };
    }
}
