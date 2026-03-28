package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnalysisResultDto {

    /** 분석 세션 ID (PK, CHAR(13)) — 예: 20260328-0001 */
    private String        sessionId;

    /** 분석에 사용된 파일 URL */
    private String        fileUrl;

    private String        chargerId;
    private LocalDateTime analyzedAt;
    private int           totalTransaction;
    private int           faultTransactionCount;
    private String        analysisType;
    private String        fileName;
    private String        summary;
    private List<FaultDetectionDto> detections;
}
