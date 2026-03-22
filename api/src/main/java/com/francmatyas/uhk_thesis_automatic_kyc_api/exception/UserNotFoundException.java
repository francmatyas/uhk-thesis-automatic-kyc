package com.francmatyas.uhk_thesis_automatic_kyc_api.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
