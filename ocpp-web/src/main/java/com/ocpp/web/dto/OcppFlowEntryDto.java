package com.ocpp.web.dto;

import lombok.Data;

@Data
public class OcppFlowEntryDto {
    private Long   id;
    private String sessionId;
    private String messageId;
    private String transactionId;
    private String chargerId;
    private String timestamp;
    private String action;
    private String messageType;
    private String direction;
    private String status;
    private String detailText;
}
