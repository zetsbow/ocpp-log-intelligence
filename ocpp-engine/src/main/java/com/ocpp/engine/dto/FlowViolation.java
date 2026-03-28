package com.ocpp.engine.dto;

import lombok.Data;

@Data
public class FlowViolation {
    private Long id;
    private String sessionId;

    /** 충전기 ID */
    private String chargerId;

    /** 트랜잭션 ID (해당 세션 식별) */
    private String transactionId;

    /** 심각도: ERROR / WARN */
    private String severity;

    /** 이슈 발생 시각 */
    private String timestamp;

    /** 이슈 내용 */
    private String message;
}
