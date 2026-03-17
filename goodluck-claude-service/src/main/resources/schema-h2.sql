-- 会话表
CREATE TABLE IF NOT EXISTS claude_session (
    id              BIGINT PRIMARY KEY,
    session_id      VARCHAR(64)  NOT NULL,
    project_name    VARCHAR(255),
    created_by      VARCHAR(64),
    created_name    VARCHAR(64),
    modified_by     VARCHAR(64),
    modified_name   VARCHAR(64),
    gmt_create      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    gmt_modified    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_delete       INT       DEFAULT 0,
    version         INT       DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_session_id ON claude_session(session_id);

-- 会话消息表
CREATE TABLE IF NOT EXISTS claude_session_message (
    id              BIGINT PRIMARY KEY,
    session_id      VARCHAR(64)  NOT NULL,
    project_name    VARCHAR(255) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    content         CLOB,
    created_by      VARCHAR(64),
    created_name    VARCHAR(64),
    modified_by     VARCHAR(64),
    modified_name   VARCHAR(64),
    gmt_create      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    gmt_modified    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_delete       INT       DEFAULT 0,
    version         INT       DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_msg_session_project ON claude_session_message(session_id, project_name);

