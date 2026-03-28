package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnalysisResultDto {
    private Long id;
    private String chargerId;
    private LocalDateTime analyzedAt;
    private int totalMsgCount;
    private int faultCount;
    private String analysisType;
    private String fileName;
    private String summary;
    private List<FaultDetectionDto> detections;
}
