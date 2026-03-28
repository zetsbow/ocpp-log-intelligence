package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FaultDetectionDto {
    private Long id;
    private Long analysisId;
    private String chargerId;
    private Long patternId;
    private String patternName;
    private String transactionId;
    private LocalDateTime detectedAt;
    private String triggerMsg;
    private String followMsg;
    private String severity;
    private String detail;
}
