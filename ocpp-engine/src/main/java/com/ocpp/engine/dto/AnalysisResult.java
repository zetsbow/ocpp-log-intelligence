package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 분석 이력 DTO
 *
 * [session_id] CHAR(13) PK — ocpp-web에서 파일명으로부터 추출하여 전달
 *              예) OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001
 * [fileUrl]    분석에 사용된 파일의 접근 URL (결과 화면에서 원본 파일 링크로 사용)
 *              예) http://127.0.0.1:7777/log/upload/OCPP-LOG-ANALYSIS-20260328-0001.txt
 */
@Data
public class AnalysisResult {

    /** 분석 세션 ID (PK, CHAR(13)) — 예: 20260328-0001 */
    private String        sessionId;

    /** 분석에 사용된 파일 URL */
    private String        fileUrl;

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
