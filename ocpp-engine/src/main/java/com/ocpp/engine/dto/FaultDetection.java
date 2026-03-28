package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 기능2: 탐지된 장애 결과 DTO
 */
@Data
public class FaultDetection {
    private Long id;
    private Long analysisId;
    private String chargerId;
    private Long patternId;
    private String patternName;    // JOIN용
    private String transactionId;
    private LocalDateTime detectedAt;
    private String triggerMsg;
    private String followMsg;
    private String severity;
    private String detail;
}
