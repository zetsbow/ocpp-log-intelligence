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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final OcppLogParser          parser;
    private final PatternMatcherService  patternMatcher;
    private final AnalysisResultMapper   analysisResultMapper;
    private final FaultDetectionMapper   faultDetectionMapper;

    /**
     * 기능1·2: 로그 분석 메인 로직
     * - filePath가 있으면 해당 파일을 직접 읽음
     * - logContent가 있으면 내용을 그대로 사용 (배치 스케줄러용)
     */
    @Transactional
    public AnalysisResult analyze(AnalyzeRequest request) {
        log.info("분석 시작 - 충전기: {}, 파일경로: {}", request.getChargerId(), request.getFilePath());

        // 1. 로그 내용 확보: filePath 우선 → logContent fallback
        String logContent = resolveLogContent(request);
        if (logContent == null || logContent.isBlank()) {
            throw new IllegalArgumentException("분석할 로그 내용이 없습니다. filePath 또는 logContent를 확인하세요.");
        }

        // 2. 파싱
        List<OcppMessage> allMessages = parser.parse(logContent);

        // 3. 충전기 ID + 시간 필터링
        List<OcppMessage> filtered = filterMessages(allMessages, request);
        log.info("필터링 결과: 전체 {}건 → 필터 후 {}건", allMessages.size(), filtered.size());

        // 4. 패턴 매칭
        List<FaultDetection> detections = patternMatcher.match(filtered);

        // 5. 분석 이력 저장
        AnalysisResult result = new AnalysisResult();
        result.setChargerId(request.getChargerId());
        result.setAnalyzedAt(LocalDateTime.now());
        result.setTotalMsgCount(filtered.size());
        result.setFaultCount(detections.size());
        result.setAnalysisType(request.getChargerId() != null ? "MANUAL" : "BATCH");
        result.setFileName(request.getFileName() != null ? request.getFileName()
                : (request.getFilePath() != null ? Paths.get(request.getFilePath()).getFileName().toString() : "unknown"));
        result.setSummary(buildSummary(filtered, detections));
        analysisResultMapper.insert(result);

        // 6. 탐지 결과 저장
        if (!detections.isEmpty()) {
            detections.forEach(d -> d.setAnalysisId(result.getId()));
            faultDetectionMapper.insertAll(detections);
        }

        result.setDetections(detections);
        log.info("분석 완료 - 총 {}개 메시지, {}개 장애 탐지", filtered.size(), detections.size());
        return result;
    }

    /**
     * filePath → 파일 직접 읽기 / logContent → 그대로 사용
     */
    private String resolveLogContent(AnalyzeRequest request) {
        if (request.getFilePath() != null && !request.getFilePath().isBlank()) {
            Path path = Paths.get(request.getFilePath());
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + request.getFilePath());
            }
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                log.info("파일 읽기 완료: {} ({}bytes)", path.getFileName(), content.length());
                return content;
            } catch (IOException e) {
                throw new RuntimeException("파일 읽기 실패: " + request.getFilePath(), e);
            }
        }
        return request.getLogContent();
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
        long callCount   = messages.stream().filter(OcppMessage::isCall).count();
        long resultCount = messages.stream().filter(OcppMessage::isCallResult).count();
        long errorCount  = messages.stream().filter(OcppMessage::isCallError).count();
        long highCount   = detections.stream().filter(d -> "HIGH".equals(d.getSeverity())).count();
        long medCount    = detections.stream().filter(d -> "MEDIUM".equals(d.getSeverity())).count();
        return String.format("Call:%d / CallResult:%d / CallError:%d | 장애 HIGH:%d, MEDIUM:%d",
                callCount, resultCount, errorCount, highCount, medCount);
    }
}
