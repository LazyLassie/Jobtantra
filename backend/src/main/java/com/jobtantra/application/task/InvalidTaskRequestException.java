package com.jobtantra.application.task;

public class InvalidTaskRequestException extends RuntimeException {

    public InvalidTaskRequestException(String message) {
        super(message);
    }
}