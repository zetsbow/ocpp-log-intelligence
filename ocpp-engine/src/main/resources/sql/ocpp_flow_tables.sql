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
    message_id     VARCHAR(100),
    transaction_id VARCHAR(50),
    charger_id     VARCHAR(50),
    log_timestamp  VARCHAR(30),
    action         VARCHAR(100),
    message_type   VARCHAR(20),
    direction      VARCHAR(10),
    status         VARCHAR(200),
    detail_text    TEXT,
    is_fault       VARCHAR(1)   NOT NULL DEFAULT 'N' COMMENT '장애 여부 (Y:장애, N:정상)',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id)
);

-- 기존 테이블에 message_id 컬럼이 없는 경우 추가 (이미 있으면 무시)
ALTER TABLE transaction_detail
    ADD COLUMN IF NOT EXISTS message_id VARCHAR(100)                   AFTER session_id,
    ADD COLUMN IF NOT EXISTS is_fault   VARCHAR(1) NOT NULL DEFAULT 'N' COMMENT '장애 여부 (Y:장애, N:정상)',
    MODIFY COLUMN log_timestamp VARCHAR(30);

-- ============================================================
--  is_fault 데이터 정정 (컬럼: VARCHAR Y/N 기준)
--  이전 코드에서 0/1 숫자로 저장된 경우 Y/N으로 변환
-- ============================================================
UPDATE transaction_detail SET is_fault = 'Y' WHERE is_fault = '1';
UPDATE transaction_detail SET is_fault = 'N' WHERE is_fault = '0';
UPDATE transaction_detail SET is_fault = 'N' WHERE is_fault IS NULL OR is_fault = '';

-- KEVIT 충전 흐름 위반 이슈 테이블 (세션 ID 기반)
DROP TABLE IF EXISTS flow_violation;
CREATE TABLE IF NOT EXISTS flow_violation (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    session_id     VARCHAR(50),
    message_id     VARCHAR(100),
    transaction_id VARCHAR(50),
    charger_id     VARCHAR(50),
    severity       VARCHAR(10),
    log_timestamp  VARCHAR(30),
    message        VARCHAR(1000),
    PRIMARY KEY (id),
    KEY idx_session_id (session_id)
);

-- 기존 테이블에 message_id 컬럼이 없는 경우 추가
ALTER TABLE flow_violation
    ADD COLUMN IF NOT EXISTS message_id VARCHAR(100) AFTER session_id;

-- ============================================================
--  기존 DB 컬럼 확장 (이미 테이블이 있는 경우 반드시 실행)
--  CHAR(13) → VARCHAR(50) : sessionId 길이 제한 오류 해결
-- ============================================================
ALTER TABLE analysis_result
    MODIFY COLUMN session_id VARCHAR(50) NOT NULL COMMENT '분석 세션 ID';

ALTER TABLE fault_detection
    MODIFY COLUMN analysis_id VARCHAR(50) COMMENT '분석 이력 FK (analysis_result.session_id)';
