package com.ocpp.engine.service;

import com.ocpp.engine.dto.AnalysisResult;
import com.ocpp.engine.dto.AnalyzeRequest;
import com.ocpp.engine.dto.FaultDetection;
import com.ocpp.engine.dto.OcppMessage;
import com.ocpp.engine.mapper.AnalysisResultMapper;
import com.ocpp.engine.mapper.FaultDetectionMapper;
import com.ocpp.engine.parser.OcppLogParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final OcppLogParser parser;
    private final PatternMatcherService patternMatcher;
    private final AnalysisResultMapper analysisResultMapper;
    private final FaultDetectionMapper faultDetectionMapper;

    /**
     * 기능1·2: 로그 분석 메인 로직
     */
    @Transactional
    public AnalysisResult analyze(AnalyzeRequest request) {
        log.info("분석 시작 - 충전기: {}, 파일: {}", request.getChargerId(), request.getFileName());

        // 1. 파싱
        List<OcppMessage> allMessages = parser.parse(request.getLogContent());

        // 2. 기능2: 충전기 ID + 시간 필터링
        List<OcppMessage> filtered = filterMessages(allMessages, request);

        // 3. 패턴 매칭
        List<FaultDetection> detections = patternMatcher.match(filtered);

        // 4. 분석 이력 저장
        AnalysisResult result = new AnalysisResult();
        result.setChargerId(request.getChargerId());
        result.setAnalyzedAt(LocalDateTime.now());
        result.setTotalMsgCount(filtered.size());
        result.setFaultCount(detections.size());
        result.setAnalysisType(request.getChargerId() != null ? "MANUAL" : "BATCH");
        result.setFileName(request.getFileName());
        result.setSummary(buildSummary(filtered, detections));
        analysisResultMapper.insert(result);

        // 5. 탐지 결과 저장
        if (!detections.isEmpty()) {
            detections.forEach(d -> d.setAnalysisId(result.getId()));
            faultDetectionMapper.insertAll(detections);
        }

        result.setDetections(detections);
        log.info("분석 완료 - 총 {}개 메시지, {}개 장애 탐지", filtered.size(), detections.size());
        return result;
    }

    private List<OcppMessage> filterMessages(List<OcppMessage> messages, AnalyzeRequest req) {
        return messages.stream()
                .filter(m -> {
                    if (req.getChargerId() == null || req.getChargerId().isBlank()) return true;
                    return req.getChargerId().equalsIgnoreCase(m.getChargerId());
                })
                .filter(m -> {
                    if (req.getFromTime() == null || m.getTimestamp() == null) return true;
                    return !m.getTimestamp().isBefore(req.getFromTime());
                })
                .filter(m -> {
                    if (req.getToTime() == null || m.getTimestamp() == null) return true;
                    return !m.getTimestamp().isAfter(req.getToTime());
                })
                .collect(Collectors.toList());
    }

    private String buildSummary(List<OcppMessage> messages, List<FaultDetection> detections) {
        long callCount = messages.stream().filter(OcppMessage::isCall).count();
        long resultCount = messages.stream().filter(OcppMessage::isCallResult).count();
        long errorCount = messages.stream().filter(OcppMessage::isCallError).count();
        long highCount = detections.stream().filter(d -> "HIGH".equals(d.getSeverity())).count();
        long medCount = detections.stream().filter(d -> "MEDIUM".equals(d.getSeverity())).count();
        return String.format("Call: %d / CallResult: %d / CallError: %d | 장애 HIGH: %d, MEDIUM: %d",
                callCount, resultCount, errorCount, highCount, medCount);
    }
}
