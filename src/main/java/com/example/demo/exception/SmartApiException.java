package com.example.demo.exception;

public class SmartApiException extends RuntimeException {
    public SmartApiException(String message) {
        super(message);
    }

    public SmartApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
