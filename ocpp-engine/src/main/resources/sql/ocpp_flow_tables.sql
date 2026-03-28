-- analysis_result 컬럼 추가
ALTER TABLE analysis_result
    ADD COLUMN session_id              VARCHAR(50)  DEFAULT NULL,
    ADD COLUMN transaction_count       INT          DEFAULT 0,
    ADD COLUMN error_transaction_count INT          DEFAULT 0;

-- OCPP 전문 흐름 상세 테이블 (세션 ID 기반)
DROP TABLE IF EXISTS ocpp_flow_entry;
CREATE TABLE IF NOT EXISTS transaction_detail (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    session_id     VARCHAR(50),
    transaction_id VARCHAR(50),
    charger_id     VARCHAR(50),
    log_timestamp  VARCHAR(20),
    action         VARCHAR(100),
    message_type   VARCHAR(20),
    direction      VARCHAR(10),
    status         VARCHAR(200),
    detail_text    TEXT,
    PRIMARY KEY (id),
    KEY idx_session_id (session_id)
);

-- KEVIT 충전 흐름 위반 이슈 테이블 (세션 ID 기반)
DROP TABLE IF EXISTS flow_violation;
CREATE TABLE IF NOT EXISTS flow_violation (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    session_id     VARCHAR(50),
    transaction_id VARCHAR(50),
    charger_id     VARCHAR(50),
    severity       VARCHAR(10),
    log_timestamp  VARCHAR(20),
    message        VARCHAR(1000),
    PRIMARY KEY (id),
    KEY idx_session_id (session_id)
);
