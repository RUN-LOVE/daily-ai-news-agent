package com.newsagent.news;

import com.newsagent.source.NewsSource;

import java.util.List;

public interface NewsFetcher {

    List<NewsItem> fetch(NewsSource source, int limit);
}
