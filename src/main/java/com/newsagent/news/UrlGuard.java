package com.newsagent.news;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlGuard {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "localhost.localdomain", "metadata.google.internal");

    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new NewsFetchException("新闻源地址为空");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException exception) {
            throw new NewsFetchException("新闻源地址不合法: " + rawUrl);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new NewsFetchException("只允许 http/https 新闻源: " + rawUrl);
        }
        if (uri.getUserInfo() != null) {
            throw new NewsFetchException("新闻源地址不允许包含用户信息");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new NewsFetchException("新闻源地址缺少主机名: " + rawUrl);
        }
        host = host.replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")) {
            throw new NewsFetchException("新闻源地址指向内网: " + host);
        }
        if (!host.contains(".") && !host.contains(":")) {
            throw new NewsFetchException("新闻源主机名不合法: " + host);
        }

        if (isLiteralAddress(host)) {
            for (InetAddress address : resolve(host)) {
                if (!isPublicAddress(address)) {
                    throw new NewsFetchException("新闻源地址解析到非公网地址: " + host);
                }
            }
        }
        return uri;
    }

    public void validateResolved(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new NewsFetchException("新闻源地址缺少主机名");
        }
        host = host.replace("[", "").replace("]", "");
        for (InetAddress address : resolve(host)) {
            if (!isPublicAddress(address)) {
                throw new NewsFetchException("新闻源地址解析到非公网地址: " + host);
            }
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0 || first >= 224) {
                return false;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 169 && second == 254) {
                return false;
            }
            if (first == 192 && second == 0 && (bytes[2] & 0xff) == 0) {
                return false;
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return false;
            }
        } else if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLiteralAddress(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new NewsFetchException("无法解析新闻源主机: " + host);
        }
    }
}
