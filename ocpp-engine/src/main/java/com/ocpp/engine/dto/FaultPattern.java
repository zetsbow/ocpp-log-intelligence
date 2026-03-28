package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 기능3: 장애 패턴 등록 DTO
 */
@Data
public class FaultPattern {
    private Long id;
    private String name;
    private String triggerAction;
    private String triggerStatus;
    private String followAction;
    private int withinSeconds;
    private String severity;   // HIGH / MEDIUM / LOW
    private String description;
    private int enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
