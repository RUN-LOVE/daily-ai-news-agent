package com.newsagent.news;

public class NewsFetchException extends RuntimeException {

    public NewsFetchException(String message) {
        super(message);
    }

    public NewsFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
