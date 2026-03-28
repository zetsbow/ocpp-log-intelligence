-- ============================================================
--  OCPP Log Intelligence - Schema
-- ============================================================
CREATE DATABASE IF NOT EXISTS ocpp_analyzer DEFAULT CHARACTER SET utf8mb4;
USE ocpp_analyzer;

-- 기능3: 장애 패턴 등록
CREATE TABLE IF NOT EXISTS fault_pattern (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200)  NOT NULL COMMENT '패턴명',
    trigger_action  VARCHAR(100)  NOT NULL COMMENT '트리거 액션 (예: StartTransaction)',
    trigger_status  VARCHAR(100)           COMMENT '트리거 응답 상태 (예: Invalid)',
    follow_action   VARCHAR(100)           COMMENT '이후 감지할 액션 (예: MeterValues)',
    within_seconds  INT           NOT NULL DEFAULT 60 COMMENT '허용 시간 윈도우(초)',
    severity        ENUM('HIGH','MEDIUM','LOW') NOT NULL DEFAULT 'MEDIUM',
    description     TEXT                   COMMENT '패턴 설명',
    enabled         TINYINT(1)    NOT NULL DEFAULT 1,
    created_at      DATETIME      NOT NULL DEFAULT NOW(),
    updated_at      DATETIME      NOT NULL DEFAULT NOW() ON UPDATE NOW()
) ENGINE=InnoDB COMMENT='장애 패턴 등록 테이블';

-- 기능1: 정기 분석 이력
-- session_id: CHAR(13) PK — ocpp-web에서 파일명으로부터 추출하여 전달
--             파일명: OCPP-LOG-ANALYSIS-20260328-0001.txt → session_id: 20260328-0001
CREATE TABLE IF NOT EXISTS analysis_result (
    session_id               CHAR(13)      NOT NULL COMMENT '분석 세션 ID (예: 20260328-0001)',
    charger_id               VARCHAR(100)           COMMENT '충전기 ID (NULL이면 전체 배치)',
    analyzed_at              DATETIME      NOT NULL DEFAULT NOW(),
    total_transaction        INT           NOT NULL DEFAULT 0  COMMENT '전체 트랜잭션 수',
    fault_transaction_count  INT           NOT NULL DEFAULT 0  COMMENT '장애 트랜잭션 수',
    analysis_type            ENUM('BATCH','MANUAL') NOT NULL DEFAULT 'BATCH',
    file_name                VARCHAR(500)           COMMENT '분석 대상 파일명',
    summary                  TEXT                   COMMENT '분석 요약',
    PRIMARY KEY (session_id)
) ENGINE=InnoDB COMMENT='분석 이력 테이블';

-- 기능2: 탐지된 장애
CREATE TABLE IF NOT EXISTS fault_detection (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id     CHAR(13)               COMMENT '분석 이력 FK (analysis_result.session_id)',
    charger_id      VARCHAR(100)  NOT NULL,
    pattern_id      BIGINT                 COMMENT '매칭된 패턴 FK',
    transaction_id  VARCHAR(100)           COMMENT '관련 트랜잭션 ID',
    detected_at     DATETIME      NOT NULL DEFAULT NOW(),
    trigger_msg     TEXT                   COMMENT '트리거 된 메시지 원문',
    follow_msg      TEXT                   COMMENT '이후 감지된 메시지 원문',
    severity        ENUM('HIGH','MEDIUM','LOW') NOT NULL DEFAULT 'MEDIUM',
    detail          TEXT                   COMMENT '탐지 상세 내용',
    CONSTRAINT fk_detection_pattern FOREIGN KEY (pattern_id) REFERENCES fault_pattern(id)
) ENGINE=InnoDB COMMENT='장애 탐지 결과 테이블';

-- ============================================================
--  기존 테이블 변경 (이미 테이블이 있는 경우 실행)
-- ============================================================
-- ALTER TABLE analysis_result DROP COLUMN log_key;
-- ALTER TABLE fault_detection MODIFY analysis_id CHAR(13) COMMENT '분석 이력 FK';

-- ============================================================
--  샘플 패턴 데이터
-- ============================================================
INSERT INTO fault_pattern (name, trigger_action, trigger_status, follow_action, within_seconds, severity, description) VALUES
('StartTransaction Invalid 후 MeterValues 전송',
 'StartTransaction', 'Invalid', 'MeterValues', 60, 'HIGH',
 'CSMS가 StartTransaction을 거부(Invalid)했음에도 충전기가 MeterValues 전문을 계속 전송하는 이상 패턴'),
('BootNotification 후 Heartbeat 미전송',
 'BootNotification', 'Accepted', 'Heartbeat', 300, 'MEDIUM',
 'BootNotification 수락 후 5분 이내 Heartbeat가 없으면 충전기 통신 이상 의심'),
('StopTransaction 없이 StartTransaction 재시도',
 'StartTransaction', 'Accepted', 'StartTransaction', 120, 'HIGH',
 '기존 트랜잭션 종료 없이 동일 충전기에서 StartTransaction이 재발생하는 패턴'),
('Authorize 실패 후 StartTransaction 시도',
 'Authorize', 'Invalid', 'StartTransaction', 30, 'MEDIUM',
 'Authorize 거부 후 30초 이내 StartTransaction을 시도하는 비정상 패턴');
