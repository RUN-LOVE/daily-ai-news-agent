package com.newsagent.briefing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.agent.AgentStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BriefingRepository {

    private static final String SELECT_FULL = """
            SELECT briefing_date, title, content, file_path, steps_json,
                   llm_calls, stop_reason, created_at
            FROM briefing
            WHERE briefing_date = ?
            """;

    private static final String SELECT_RECENT = """
            SELECT briefing_date, title, llm_calls, stop_reason, created_at
            FROM briefing
            ORDER BY briefing_date DESC
            LIMIT 30
            """;

    private static final String UPSERT = """
            INSERT INTO briefing (
                briefing_date, title, content, file_path, steps_json,
                llm_calls, stop_reason, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(briefing_date) DO UPDATE SET
                title = excluded.title,
                content = excluded.content,
                file_path = excluded.file_path,
                steps_json = excluded.steps_json,
                llm_calls = excluded.llm_calls,
                stop_reason = excluded.stop_reason,
                created_at = excluded.created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BriefingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Briefing> findByDate(String date) {
        List<Briefing> briefings = jdbcTemplate.query(SELECT_FULL, (resultSet, rowNumber) -> new Briefing(
                resultSet.getString("briefing_date"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("file_path"),
                readSteps(resultSet.getString("steps_json")),
                resultSet.getInt("llm_calls"),
                resultSet.getString("stop_reason"),
                resultSet.getString("created_at")
        ), date);
        return briefings.stream().findFirst();
    }

    public List<BriefingSummary> listRecent() {
        return jdbcTemplate.query(SELECT_RECENT, (resultSet, rowNumber) -> new BriefingSummary(
                resultSet.getString("briefing_date"),
                resultSet.getString("title"),
                resultSet.getInt("llm_calls"),
                resultSet.getString("stop_reason"),
                resultSet.getString("created_at")
        ));
    }

    public boolean existsByDate(String date) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM briefing WHERE briefing_date = ?", Integer.class, date);
        return count != null && count > 0;
    }

    public Briefing save(Briefing briefing) {
        jdbcTemplate.update(UPSERT,
                briefing.date(),
                briefing.title(),
                briefing.content(),
                briefing.filePath(),
                writeSteps(briefing.steps()),
                briefing.llmCalls(),
                briefing.stopReason(),
                briefing.createdAt());
        return briefing;
    }

    private String writeSteps(List<AgentStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps == null ? List.of() : steps);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 轨迹序列化失败", exception);
        }
    }

    private List<AgentStep> readSteps(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<AgentStep>>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
