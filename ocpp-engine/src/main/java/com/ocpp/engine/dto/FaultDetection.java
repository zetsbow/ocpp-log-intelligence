package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 기능2: 탐지된 장애 결과 DTO
 */
@Data
public class FaultDetection {
    private Long          id;

    /** 분석 세션 ID FK (analysis_result.session_id 참조, CHAR(13)) */
    private String        analysisId;

    private String        chargerId;
    private Long          patternId;
    private String        patternName;
    private String        transactionId;
    private LocalDateTime detectedAt;
    private String        triggerMsg;
    private String        followMsg;
    private String        severity;
    private String        detail;
}
