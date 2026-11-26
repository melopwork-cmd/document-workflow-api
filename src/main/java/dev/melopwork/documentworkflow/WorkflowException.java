package dev.melopwork.documentworkflow;

final class WorkflowException extends RuntimeException {
    enum Reason { NOT_FOUND, VERSION_CONFLICT, INVALID_TRANSITION }

    private final Reason reason;

    WorkflowException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }
}
