package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기능1: 분석 이력 및 결과 DTO
 */
@Data
public class AnalysisResult {
    private Long id;
    private String chargerId;
    private LocalDateTime analyzedAt;
    private int totalMsgCount;
    private int faultCount;
    private String analysisType;  // BATCH / MANUAL
    private String fileName;
    private String summary;

    /** 탐지된 장애 목록 (조회 전용) */
    private List<FaultDetection> detections;
}
