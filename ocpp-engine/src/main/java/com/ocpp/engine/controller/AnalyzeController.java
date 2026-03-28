package com.ocpp.engine.controller;

import com.ocpp.engine.dto.AnalysisResult;
import com.ocpp.engine.dto.AnalyzeRequest;
import com.ocpp.engine.mapper.AnalysisResultMapper;
import com.ocpp.engine.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/analyze")
@RequiredArgsConstructor
public class AnalyzeController {

    private final AnalysisService      analysisService;
    private final AnalysisResultMapper analysisResultMapper;

    /** POST /api/analyze/charger */
    @PostMapping("/charger")
    public ResponseEntity<AnalysisResult> analyzeCharger(@RequestBody AnalyzeRequest request) {

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("[AnalyzeController] POST /api/analyze/charger 수신");
        log.info("  ├─ chargerId  : {}", isBlank(request.getChargerId())  ? "(전체 분석)" : request.getChargerId());
        log.info("  ├─ fromTime   : {}", request.getFromTime()  != null   ? request.getFromTime()  : "(제한 없음)");
        log.info("  ├─ toTime     : {}", request.getToTime()    != null   ? request.getToTime()    : "(제한 없음)");
        log.info("  ├─ fileName   : {}", isBlank(request.getFileName())   ? "(없음)" : request.getFileName());
        log.info("  ├─ fileUrl    : {}", isBlank(request.getFileUrl())    ? "(없음)" : request.getFileUrl());
        log.info("  └─ logContent : {}", request.getLogContent() != null
                ? request.getLogContent().length() + " bytes" : "(없음)");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        AnalysisResult result = analysisService.analyze(request);

        log.info("[AnalyzeController] 분석 완료 - sessionId={}, totalTransaction={}, faultTransactionCount={}",
                result.getSessionId(), result.getTotalTransaction(), result.getFaultTransactionCount());

        return ResponseEntity.ok(result);
    }

    /** POST /api/analyze/batch */
    @PostMapping("/batch")
    public ResponseEntity<AnalysisResult> analyzeBatch(@RequestBody AnalyzeRequest request) {

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("[AnalyzeController] POST /api/analyze/batch 수신");
        log.info("  ├─ fileName : {}", isBlank(request.getFileName()) ? "(없음)" : request.getFileName());
        log.info("  └─ fileUrl  : {}", isBlank(request.getFileUrl())  ? "(없음)" : request.getFileUrl());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        request.setChargerId(null);
        AnalysisResult result = analysisService.analyze(request);

        log.info("[AnalyzeController] 배치 완료 - sessionId={}, totalTransaction={}, faultTransactionCount={}",
                result.getSessionId(), result.getTotalTransaction(), result.getFaultTransactionCount());

        return ResponseEntity.ok(result);
    }

    /** GET /api/analyze/history?limit=20 */
    @GetMapping("/history")
    public ResponseEntity<List<AnalysisResult>> getHistory(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(analysisResultMapper.findRecent(limit));
    }

    /** GET /api/analyze/history/{sessionId} */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<AnalysisResult> getHistoryById(@PathVariable String sessionId) {
        AnalysisResult result = analysisResultMapper.findById(sessionId);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
