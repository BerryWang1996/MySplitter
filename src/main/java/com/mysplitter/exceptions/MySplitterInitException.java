package com.mysplitter.exceptions;

public class MySplitterInitException extends Exception {

    public MySplitterInitException(Exception e) {
        super("MySplitter initialized failed!", e);
    }
}
