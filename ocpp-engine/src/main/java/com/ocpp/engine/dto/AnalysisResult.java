package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기능1: 분석 이력 및 결과 DTO
 */
@Data
public class AnalysisResult {
    private String chargerId;
    private LocalDateTime analyzedAt;
    private int totalTransaction;
    private int faultTransactionCount;
    private String analysisType;  // BATCH / MANUAL
    private String fileName;
    private String summary;

    /** 세션 ID: 파일명의 마지막 _ 뒤 문자열 (ex: 20260328-0001) */
    private String sessionId;

    /** 탐지된 장애 목록 (조회 전용) */
    private List<FaultDetection> detections;
}
