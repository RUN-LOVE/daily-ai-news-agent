CREATE TABLE IF NOT EXISTS profile (
    id                    INTEGER PRIMARY KEY CHECK (id = 1),
    display_name          TEXT NOT NULL,
    role                  TEXT NOT NULL DEFAULT '',
    email                 TEXT NOT NULL DEFAULT '',
    topics_json           TEXT NOT NULL DEFAULT '[]',
    keywords_json         TEXT NOT NULL DEFAULT '[]',
    exclude_keywords_json TEXT NOT NULL DEFAULT '[]',
    sources_json          TEXT NOT NULL DEFAULT '[]',
    language              TEXT NOT NULL DEFAULT 'zh',
    max_items             INTEGER NOT NULL DEFAULT 8,
    deliver_time          TEXT NOT NULL DEFAULT '08:30',
    push_channel          TEXT NOT NULL DEFAULT 'INBOX',
    updated_at            TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS briefing (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    briefing_date TEXT NOT NULL UNIQUE,
    title         TEXT NOT NULL,
    content       TEXT NOT NULL,
    file_path     TEXT NOT NULL DEFAULT '',
    steps_json    TEXT NOT NULL DEFAULT '[]',
    llm_calls     INTEGER NOT NULL DEFAULT 0,
    stop_reason   TEXT NOT NULL DEFAULT 'COMPLETED',
    created_at    TEXT NOT NULL
);
