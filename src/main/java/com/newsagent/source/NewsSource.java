package com.newsagent.source;

public record NewsSource(
        String id,
        String name,
        String url,
        String type,
        String language,
        String focus
) {
}
