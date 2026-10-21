package com.newsagent.news;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlGuardTest {

    private final UrlGuard urlGuard = new UrlGuard();

    @Test
    void rejectsLocalAndPrivateTargets() {
        String[] urls = {
                "file:///etc/passwd",
                "http://localhost/feed",
                "http://service.local/feed",
                "http://metadata.google.internal/feed",
                "http://intranet/feed",
                "http://127.0.0.1/feed",
                "http://10.1.2.3/feed",
                "http://192.168.1.10/feed",
                "http://169.254.169.254/latest/meta-data",
                "http://100.64.0.1/feed",
                "http://[::1]/feed",
                "http://user:pass@example.com/feed"
        };

        for (String url : urls) {
            assertThrows(NewsFetchException.class, () -> urlGuard.validate(url), url);
        }
    }

    @Test
    void acceptsPublicFeedUrls() {
        assertDoesNotThrow(() -> urlGuard.validate("https://example.com/rss"));
        assertDoesNotThrow(() -> urlGuard.validate("https://feeds.example.com/rss.xml"));
        assertDoesNotThrow(() -> urlGuard.validate("https://8.8.8.8/feed"));
    }

    @Test
    void classifiesAddresses() throws Exception {
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("10.0.0.1")));
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("192.168.1.1")));
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("169.254.1.1")));
        assertFalse(UrlGuard.isPublicAddress(InetAddress.getByName("::1")));
        assertTrue(UrlGuard.isPublicAddress(InetAddress.getByName("8.8.8.8")));
    }
}
