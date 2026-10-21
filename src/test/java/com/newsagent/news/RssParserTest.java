package com.newsagent.news;

import com.newsagent.source.NewsSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssParserTest {

    private static final NewsSource SOURCE = new NewsSource(
            "test", "测试源", "https://example.com/feed", "rss", "zh", "AI");

    @Test
    void parsesRssItemsAndCleansHtml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Test</title>
                    <item>
                      <title><![CDATA[大模型 <b>新进展</b>]]></title>
                      <link>https://example.com/a</link>
                      <pubDate>Wed, 21 Oct 2026 08:00:00 GMT</pubDate>
                      <description><![CDATA[<p>Hello&nbsp;Agent</p><script>alert(1)</script>]]></description>
                    </item>
                    <item>
                      <title>Second</title>
                      <link>https://example.com/b</link>
                    </item>
                  </channel>
                </rss>
                """;

        List<NewsItem> items = RssParser.parse(xml.getBytes(StandardCharsets.UTF_8), SOURCE, 10);

        assertEquals(2, items.size());
        assertEquals("大模型 新进展", items.get(0).title());
        assertEquals("https://example.com/a", items.get(0).link());
        assertTrue(items.get(0).publishedAt().startsWith("2026-10-21"));
        assertTrue(items.get(0).summary().contains("Hello Agent"));
        assertFalse(items.get(0).summary().contains("alert"));
        assertEquals("test", items.get(0).sourceId());
        assertEquals("测试源", items.get(0).sourceName());
    }

    @Test
    void parsesAtomEntries() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom</title>
                  <entry>
                    <title>OpenAI release</title>
                    <link rel="alternate" href="https://openai.com/news/x"/>
                    <updated>2026-10-20T10:00:00Z</updated>
                    <summary>Short summary</summary>
                  </entry>
                </feed>
                """;

        List<NewsItem> items = RssParser.parse(xml.getBytes(StandardCharsets.UTF_8), SOURCE, 10);

        assertEquals(1, items.size());
        assertEquals("OpenAI release", items.get(0).title());
        assertEquals("https://openai.com/news/x", items.get(0).link());
        assertTrue(items.get(0).publishedAt().startsWith("2026-10-20"));
        assertEquals("Short summary", items.get(0).summary());
    }

    @Test
    void respectsLimit() {
        String xml = """
                <rss><channel>
                  <item><title>One</title></item>
                  <item><title>Two</title></item>
                  <item><title>Three</title></item>
                </channel></rss>
                """;

        List<NewsItem> items = RssParser.parse(xml.getBytes(StandardCharsets.UTF_8), SOURCE, 1);

        assertEquals(1, items.size());
        assertEquals("One", items.get(0).title());
    }

    @Test
    void rejectsDoctypeToPreventXxe() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <rss><channel><item><title>&xxe;</title></item></channel></rss>
                """;

        assertThrows(NewsFetchException.class,
                () -> RssParser.parse(xml.getBytes(StandardCharsets.UTF_8), SOURCE, 10));
    }

    @Test
    void rejectsMalformedXml() {
        assertThrows(NewsFetchException.class,
                () -> RssParser.parse("<rss>".getBytes(StandardCharsets.UTF_8), SOURCE, 10));
    }
}
