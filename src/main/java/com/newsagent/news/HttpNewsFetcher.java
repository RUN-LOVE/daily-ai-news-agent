package com.newsagent.news;

import com.newsagent.source.NewsSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class HttpNewsFetcher implements NewsFetcher {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private final UrlGuard urlGuard;
    private final HttpClient httpClient;

    public HttpNewsFetcher(UrlGuard urlGuard) {
        this.urlGuard = urlGuard;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public List<NewsItem> fetch(NewsSource source, int limit) {
        if (source == null) {
            throw new NewsFetchException("新闻源不能为空");
        }

        URI uri = urlGuard.validate(source.url());
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            urlGuard.validateResolved(uri);
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                String location = response.headers().firstValue("Location").orElse("");
                if (location.isBlank()) {
                    throw new NewsFetchException(source.name() + " 返回重定向但缺少 Location");
                }
                uri = urlGuard.validate(uri.resolve(location).toString());
                continue;
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new NewsFetchException(source.name() + " 抓取失败（HTTP " + status + "）");
            }

            try (InputStream body = response.body()) {
                byte[] bytes = readLimited(body);
                return RssParser.parse(bytes, source, limit);
            } catch (IOException exception) {
                throw new NewsFetchException(source.name() + " 响应读取失败: " + exception.getMessage(), exception);
            }
        }
        throw new NewsFetchException(source.name() + " 重定向次数过多");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                .header("User-Agent", "daily-ai-news-agent/0.1")
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NewsFetchException("新闻抓取被中断", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new NewsFetchException("新闻抓取失败: " + exception.getMessage(), exception);
        }
    }

    private static byte[] readLimited(InputStream body) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = body.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY_BYTES) {
                throw new NewsFetchException("新闻源响应超过 2MB，已拒绝");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void closeQuietly(InputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException ignored) {
            // Nothing useful to do while discarding a response.
        }
    }
}
