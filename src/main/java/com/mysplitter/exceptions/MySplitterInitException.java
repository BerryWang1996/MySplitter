package com.mysplitter.exceptions;

/**
 * @Author: wangbor
 * @Date: 2018/5/18 16:41
 */
public class MySplitterInitException extends Exception {

    public MySplitterInitException(Exception e) {
        super("MySplitter initialized failed!", e);
    }
}
