package com.mysplitter.exceptions;

public class NoHealthyDataSourceException extends RuntimeException {

    public NoHealthyDataSourceException() {
    }

    public NoHealthyDataSourceException(String message) {
        super(message);
    }
}
