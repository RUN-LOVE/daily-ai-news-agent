package com.newsagent.profile;

import com.newsagent.source.NewsSource;
import com.newsagent.source.SourceCatalog;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private static final int DEFAULT_MAX_ITEMS = 8;
    private static final String DEFAULT_DELIVER_TIME = "08:30";

    private final ProfileRepository profileRepository;
    private final SourceCatalog sourceCatalog;

    public ProfileService(ProfileRepository profileRepository, SourceCatalog sourceCatalog) {
        this.profileRepository = profileRepository;
        this.sourceCatalog = sourceCatalog;
    }

    public Profile get() {
        return profileRepository.find().orElseGet(() -> profileRepository.save(defaultProfile()));
    }

    public Profile update(ProfileRequest request) {
        List<String> sources = normalize(request.sources());
        Set<String> knownSourceIds = sourceCatalog.list().stream()
                .map(NewsSource::id)
                .collect(Collectors.toSet());
        if (!knownSourceIds.containsAll(sources)) {
            throw new IllegalArgumentException("包含未知的新闻源");
        }

        String email = trimToEmpty(request.email());
        String pushChannel = defaultIfBlank(request.pushChannel(), "INBOX").toUpperCase(Locale.ROOT);
        if ("EMAIL".equals(pushChannel) && email.isBlank()) {
            throw new IllegalArgumentException("选择邮件推送时必须填写邮箱");
        }

        Profile profile = new Profile(
                request.displayName().trim(),
                trimToEmpty(request.role()),
                email,
                normalize(request.topics()),
                normalize(request.keywords()),
                normalize(request.excludeKeywords()),
                sources,
                defaultIfBlank(request.language(), "zh"),
                request.maxItems() == null ? DEFAULT_MAX_ITEMS : request.maxItems(),
                defaultIfBlank(request.deliverTime(), DEFAULT_DELIVER_TIME),
                pushChannel,
                Instant.now().toString()
        );
        return profileRepository.save(profile);
    }

    private Profile defaultProfile() {
        List<String> defaultSources = sourceCatalog.list().stream()
                .limit(4)
                .map(NewsSource::id)
                .toList();
        return new Profile(
                "AI 探索者",
                "开发者",
                "",
                List.of("大模型", "AI Agent"),
                List.of(),
                List.of(),
                defaultSources,
                "zh",
                DEFAULT_MAX_ITEMS,
                DEFAULT_DELIVER_TIME,
                "INBOX",
                Instant.now().toString()
        );
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
