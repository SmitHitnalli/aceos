package com.aceos.service;

public class AceOsServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    public AceOsServiceException(
            String message) {
        super(message);
    }

    public AceOsServiceException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}