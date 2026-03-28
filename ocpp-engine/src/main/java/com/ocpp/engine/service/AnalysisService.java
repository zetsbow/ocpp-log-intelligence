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
 * [로그 내용 확보 우선순위]
 * 1. fileUrl  → web 서버 URL 호출로 파일 내용 다운로드 (기본)
 * 2. logContent → 배치 스케줄러가 직접 내용 전달할 때
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

    @Transactional
    public AnalysisResult analyze(AnalyzeRequest request) {
        log.info("분석 시작 - chargerId={}, fileUrl={}", request.getChargerId(), request.getFileUrl());

        // 1. 파일 내용 읽기
        String logContent = readLogContent(request);

        // 2. 파싱 → 필터링 → 패턴 매칭
        List<OcppMessage> allMessages = parser.parse(logContent);
        List<OcppMessage> filtered    = filterMessages(allMessages, request);
        log.info("파싱 완료: 전체 {}건 → 필터 후 {}건", allMessages.size(), filtered.size());

        List<FaultDetection> detections = patternMatcher.match(filtered);

        // 3. 이력 저장
        AnalysisResult result = new AnalysisResult();
        result.setChargerId(request.getChargerId());
        result.setAnalyzedAt(LocalDateTime.now());
        result.setTotalMsgCount(filtered.size());
        result.setFaultCount(detections.size());
        result.setAnalysisType(isBlank(request.getChargerId()) ? "BATCH" : "MANUAL");
        result.setFileName(resolveFileName(request));
        result.setSummary(buildSummary(filtered, detections));
        analysisResultMapper.insert(result);

        // 4. 탐지 결과 저장
        if (!detections.isEmpty()) {
            detections.forEach(d -> d.setAnalysisId(result.getId()));
            faultDetectionMapper.insertAll(detections);
        }

        result.setDetections(detections);
        log.info("분석 완료 - 메시지 {}건, 장애 {}건", filtered.size(), detections.size());
        return result;
    }

    /**
     * 로그 내용 확보
     * 1순위: fileUrl → HTTP GET으로 web 서버에서 파일 내용 다운로드
     * 2순위: logContent → 배치 스케줄러 직접 전달
     */
    private String readLogContent(AnalyzeRequest request) {

        // ── 1순위: fileUrl (URL 인코딩된 주소로 HTTP GET 호출) ──
        if (!isBlank(request.getFileUrl())) {
            log.info("[AnalysisService] fileUrl 호출: {}", request.getFileUrl());
            try {
                // URI.create()를 사용하면 이미 인코딩된 URL도 그대로 사용 가능
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

        // ── 2순위: logContent (배치 스케줄러) ──────────────────
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
            return url.substring(url.lastIndexOf('/') + 1); // 인코딩된 파일명 그대로
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
