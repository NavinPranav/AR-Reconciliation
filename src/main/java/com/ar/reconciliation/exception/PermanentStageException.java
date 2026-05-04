package com.ar.reconciliation.exception;

public class PermanentStageException extends RuntimeException {
    public PermanentStageException(String message) {
        super(message);
    }

    public PermanentStageException(String message, Throwable cause) {
        super(message, cause);
    }
}
