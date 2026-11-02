package com.patientqueue.system.exception;

public class DuplicateCheckInException extends RuntimeException {
    public DuplicateCheckInException(String message) {
        super(message);
    }
}
