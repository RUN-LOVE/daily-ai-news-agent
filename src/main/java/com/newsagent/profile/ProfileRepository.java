package com.newsagent.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProfileRepository {

    private static final String SELECT_PROFILE = """
            SELECT display_name, role, email, topics_json, keywords_json,
                   exclude_keywords_json, sources_json, language, max_items,
                   deliver_time, push_channel, updated_at
            FROM profile
            WHERE id = 1
            """;

    private static final String UPSERT_PROFILE = """
            INSERT INTO profile (
                id, display_name, role, email, topics_json, keywords_json,
                exclude_keywords_json, sources_json, language, max_items,
                deliver_time, push_channel, updated_at
            ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                display_name = excluded.display_name,
                role = excluded.role,
                email = excluded.email,
                topics_json = excluded.topics_json,
                keywords_json = excluded.keywords_json,
                exclude_keywords_json = excluded.exclude_keywords_json,
                sources_json = excluded.sources_json,
                language = excluded.language,
                max_items = excluded.max_items,
                deliver_time = excluded.deliver_time,
                push_channel = excluded.push_channel,
                updated_at = excluded.updated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Profile> find() {
        List<Profile> profiles = jdbcTemplate.query(SELECT_PROFILE, (resultSet, rowNumber) -> new Profile(
                resultSet.getString("display_name"),
                resultSet.getString("role"),
                resultSet.getString("email"),
                readList(resultSet.getString("topics_json")),
                readList(resultSet.getString("keywords_json")),
                readList(resultSet.getString("exclude_keywords_json")),
                readList(resultSet.getString("sources_json")),
                resultSet.getString("language"),
                resultSet.getInt("max_items"),
                resultSet.getString("deliver_time"),
                resultSet.getString("push_channel"),
                resultSet.getString("updated_at")
        ));
        return profiles.stream().findFirst();
    }

    public Profile save(Profile profile) {
        jdbcTemplate.update(UPSERT_PROFILE,
                profile.displayName(),
                profile.role(),
                profile.email(),
                writeList(profile.topics()),
                writeList(profile.keywords()),
                writeList(profile.excludeKeywords()),
                writeList(profile.sources()),
                profile.language(),
                profile.maxItems(),
                profile.deliverTime(),
                profile.pushChannel(),
                profile.updatedAt());
        return profile;
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("偏好列表序列化失败", exception);
        }
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
