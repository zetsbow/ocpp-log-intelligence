package com.ocpp.engine.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnalysisResult {

    private String        sessionId;
    private String        fileUrl;
    private String        chargerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analyzedAt;

    private int           totalTransaction;
    private int           faultTransactionCount;
    private String        analysisType;
    private String        fileName;
    private String        summary;

    private List<FaultDetection> detections;
}
