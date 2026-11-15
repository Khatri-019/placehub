package com.placehub.user.dto;

final class Validation {

    static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    static final String PASSWORD_MESSAGE = "must contain at least one letter and one digit";

    private Validation() {
    }
}
