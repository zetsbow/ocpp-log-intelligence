package com.ocpp.web.dto;

import lombok.Data;

@Data
public class FlowViolationDto {
    private Long   id;
    private String sessionId;
    private String transactionId;
    private String chargerId;
    private String severity;      // ERROR / WARN
    private String timestamp;     // log_timestamp
    private String message;
}
