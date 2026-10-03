package com.jobtantra.application.auth;

public class InvalidAccountRequestException extends RuntimeException {

    public InvalidAccountRequestException(String message) {
        super(message);
    }
}