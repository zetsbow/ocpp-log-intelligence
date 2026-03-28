package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnalyzeRequestDto {
    private String chargerId;
    private LocalDateTime fromTime;
    private LocalDateTime toTime;
    private String logContent;
    private String fileName;
}
