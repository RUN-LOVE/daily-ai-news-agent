package com.newsagent.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class SourceCatalog {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SourceCatalog(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<NewsSource> list() {
        Path file = sourceFile();
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("新闻源配置不存在: " + file);
        }
        try {
            List<NewsSource> sources = objectMapper.readValue(
                    file.toFile(), new TypeReference<List<NewsSource>>() {});
            return sources.stream()
                    .filter(source -> source != null
                            && hasText(source.id())
                            && hasText(source.name())
                            && hasText(source.url()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("新闻源配置读取失败: " + exception.getMessage(), exception);
        }
    }

    private Path sourceFile() {
        return Path.of(properties.workspace())
                .resolve("sources")
                .resolve("feeds.json")
                .toAbsolutePath()
                .normalize();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
