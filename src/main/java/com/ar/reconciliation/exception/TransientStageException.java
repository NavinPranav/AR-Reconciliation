package com.ar.reconciliation.exception;

public class TransientStageException extends RuntimeException {
    public TransientStageException(String message) {
        super(message);
    }

    public TransientStageException(String message, Throwable cause) {
        super(message, cause);
    }
}
