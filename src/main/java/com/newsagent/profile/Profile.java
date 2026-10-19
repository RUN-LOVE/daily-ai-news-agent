package com.newsagent.profile;

import java.util.List;

public record Profile(
        String displayName,
        String role,
        String email,
        List<String> topics,
        List<String> keywords,
        List<String> excludeKeywords,
        List<String> sources,
        String language,
        int maxItems,
        String deliverTime,
        String pushChannel,
        String updatedAt
) {
}
