package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FaultPatternDto {
    private Long id;
    private String name;
    private String triggerAction;
    private String triggerStatus;
    private String followAction;
    private int withinSeconds;
    private String severity;
    private String description;
    private int enabled;
    private LocalDateTime createdAt;
}
