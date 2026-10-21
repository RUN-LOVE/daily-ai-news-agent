package com.newsagent.news;

public record NewsItem(
        String sourceId,
        String sourceName,
        String title,
        String link,
        String publishedAt,
        String summary
) {
}
