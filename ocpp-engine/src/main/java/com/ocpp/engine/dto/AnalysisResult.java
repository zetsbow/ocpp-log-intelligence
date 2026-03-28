package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 분석 이력 DTO
 *
 * [session_id] CHAR(13) PK — log_key와 동일값 사용
 *              예) 20260328-0001 (13자리)
 * [log_key]    파일명에서 추출 (예: OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001)
 */
@Data
public class AnalysisResult {

    /** 분석 세션 ID (PK, CHAR(13)) — 예: 20260328-0001 */
    private String        sessionId;

    /** 로그 파일 키 (예: 20260328-0001) */
    private String        logKey;

    private String        chargerId;
    private LocalDateTime analyzedAt;
    private int           totalTransaction;
    private int           faultTransactionCount;
    private String        analysisType;  // BATCH / MANUAL
    private String        fileName;
    private String        summary;

    /** 탐지된 장애 목록 (조회 전용) */
    private List<FaultDetection> detections;
}
