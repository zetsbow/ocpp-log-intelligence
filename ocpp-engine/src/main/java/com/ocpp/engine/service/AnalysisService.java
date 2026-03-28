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
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 로그 분석 서비스
 *
 * [sessionId] ocpp-web이 파일명에서 추출하여 request에 담아 전달
 * [fileUrl]   request.fileUrl → result.fileUrl 에 그대로 세팅 (결과 화면 링크용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final OcppLogParser         parser;
    private final PatternMatcherService patternMatcher;
    private final AnalysisResultMapper  analysisResultMapper;
    private final FaultDetectionMapper  faultDetectionMapper;
    private final RestTemplate          restTemplate;

    private static final String FILE_PREFIX = "OCPP-LOG-ANALYSIS-";

    @Transactional
    public AnalysisResult analyze(AnalyzeRequest request) {
        log.info("분석 시작 - sessionId={}, chargerId={}, fileUrl={}",
                request.getSessionId(), request.getChargerId(), request.getFileUrl());

        // 1. 파일 내용 읽기
        String logContent = readLogContent(request);

        // 2. 파싱 → 필터링 → 패턴 매칭
        List<OcppMessage> allMessages = parser.parse(logContent);
        List<OcppMessage> filtered    = filterMessages(allMessages, request);
        log.info("파싱 완료: 전체 {}건 → 필터 후 {}건", allMessages.size(), filtered.size());

        List<FaultDetection> detections = patternMatcher.match(filtered);

        // 3. 파일명 및 sessionId 결정
        String fileName  = resolveFileName(request);
        String sessionId = request.getSessionId();

        // 4. 분석 이력 저장
        AnalysisResult result = new AnalysisResult();
        result.setSessionId(sessionId);
        result.setFileUrl(request.getFileUrl());        // ← fileUrl 세팅
        result.setChargerId(request.getChargerId());
        result.setAnalyzedAt(LocalDateTime.now());
        result.setTotalTransaction(filtered.size());
        result.setFaultTransactionCount(detections.size());
        result.setAnalysisType(isBlank(request.getChargerId()) ? "BATCH" : "MANUAL");
        result.setFileName(fileName);
        result.setSummary(buildSummary(filtered, detections));
        analysisResultMapper.insert(result);

        log.info("분석 이력 저장 - sessionId={}, fileUrl={}", sessionId, request.getFileUrl());

        // 5. 탐지 결과 저장
        if (!detections.isEmpty()) {
            detections.forEach(d -> d.setAnalysisId(sessionId));
            faultDetectionMapper.insertAll(detections);
        }

        result.setDetections(detections);
        log.info("분석 완료 - sessionId={}, 트랜잭션 {}건, 장애 {}건",
                sessionId, filtered.size(), detections.size());
        return result;
    }

    private String readLogContent(AnalyzeRequest request) {
        if (!isBlank(request.getFileUrl())) {
            log.info("[AnalysisService] fileUrl 호출: {}", request.getFileUrl());
            try {
                String content = restTemplate.getForObject(
                        URI.create(request.getFileUrl()), String.class);
                if (content == null || content.isBlank()) {
                    throw new RuntimeException("파일 내용이 비어있습니다: " + request.getFileUrl());
                }
                log.info("[AnalysisService] 파일 다운로드 완료: {} bytes", content.length());
                return content;
            } catch (Exception e) {
                throw new RuntimeException(
                        "파일 URL 호출 실패: " + request.getFileUrl() + " → " + e.getMessage(), e);
            }
        }
        if (!isBlank(request.getLogContent())) {
            log.info("[AnalysisService] logContent 직접 사용: {} bytes",
                    request.getLogContent().length());
            return request.getLogContent();
        }
        throw new IllegalArgumentException(
                "분석할 로그가 없습니다. fileUrl 또는 logContent를 확인하세요.");
    }

    private List<OcppMessage> filterMessages(List<OcppMessage> messages, AnalyzeRequest req) {
        return messages.stream()
                .filter(m -> isBlank(req.getChargerId())
                        || req.getChargerId().equalsIgnoreCase(m.getChargerId()))
                .filter(m -> req.getFromTime() == null || m.getTimestamp() == null
                        || !m.getTimestamp().isBefore(req.getFromTime()))
                .filter(m -> req.getToTime() == null || m.getTimestamp() == null
                        || !m.getTimestamp().isAfter(req.getToTime()))
                .collect(Collectors.toList());
    }

    private String resolveFileName(AnalyzeRequest req) {
        if (!isBlank(req.getFileName())) return req.getFileName();
        if (!isBlank(req.getFileUrl())) {
            String url = req.getFileUrl();
            return url.substring(url.lastIndexOf('/') + 1);
        }
        return "unknown";
    }

    private String buildSummary(List<OcppMessage> msgs, List<FaultDetection> detections) {
        long call   = msgs.stream().filter(OcppMessage::isCall).count();
        long result = msgs.stream().filter(OcppMessage::isCallResult).count();
        long error  = msgs.stream().filter(OcppMessage::isCallError).count();
        long high   = detections.stream().filter(d -> "HIGH".equals(d.getSeverity())).count();
        long med    = detections.stream().filter(d -> "MEDIUM".equals(d.getSeverity())).count();
        return String.format("Call:%d / CallResult:%d / CallError:%d | HIGH:%d MEDIUM:%d",
                call, result, error, high, med);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
