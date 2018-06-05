package com.mysplitter.exceptions;

/**
 * @Author: 王伯瑞
 * @Date: 2018/5/28 16:49
 */
public class NoHealthyDataSourceException extends RuntimeException {

    public NoHealthyDataSourceException() {
    }

    public NoHealthyDataSourceException(String message) {
        super(message);
    }
}
