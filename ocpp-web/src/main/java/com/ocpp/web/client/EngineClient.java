package com.ocpp.web.client;

import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.AnalyzeRequestDto;
import com.ocpp.web.dto.FaultPatternDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * ocpp-web → ocpp-engine 내부 REST 통신 클라이언트
 */
@Slf4j
@Component
public class EngineClient {

    private final RestTemplate restTemplate;

    @Value("${engine.url:http://localhost:7778}")
    private String engineUrl;

    public EngineClient() {
        this.restTemplate = new RestTemplate();
    }

    /* ── 분석 API ───────────────────────────────────────── */

    /** 기능2: 특정 충전기·시간 구간 분석 */
    public AnalysisResultDto analyzeCharger(AnalyzeRequestDto request) {
        String url = engineUrl + "/api/analyze/charger";

        // ── 요청 파라미터 상세 로그 ──────────────────────────
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("[EngineClient] POST {}", url);
        log.info("[EngineClient] 요청 파라미터");
        log.info("  ├─ chargerId  : {}", request.getChargerId()  != null ? request.getChargerId()  : "(전체 분석)");
        log.info("  ├─ fromTime   : {}", request.getFromTime()   != null ? request.getFromTime()   : "(제한 없음)");
        log.info("  ├─ toTime     : {}", request.getToTime()     != null ? request.getToTime()     : "(제한 없음)");
        log.info("  ├─ fileName   : {}", request.getFileName()   != null ? request.getFileName()   : "(없음)");
        log.info("  ├─ filePath   : {}", request.getFilePath()   != null ? request.getFilePath()   : "(없음)");
        log.info("  └─ logContent : {}", request.getLogContent() != null
                ? request.getLogContent().length() + " bytes"
                : "(없음)");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            AnalysisResultDto result = post(url, request, AnalysisResultDto.class);
            log.info("[EngineClient] 응답 수신 - totalMsgCount={}, faultCount={}",
                    result != null ? result.getTotalMsgCount() : "null",
                    result != null ? result.getFaultCount()    : "null");
            return result;
        } catch (RestClientException e) {
            log.error("[EngineClient] engine 호출 실패 - url={}, error={}", url, e.getMessage());
            throw e;
        }
    }

    /** 기능1: 배치 전체 분석 */
    public AnalysisResultDto analyzeBatch(AnalyzeRequestDto request) {
        String url = engineUrl + "/api/analyze/batch";

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("[EngineClient] POST {} (배치)", url);
        log.info("  ├─ fileName : {}", request.getFileName() != null ? request.getFileName() : "(없음)");
        log.info("  └─ filePath : {}", request.getFilePath() != null ? request.getFilePath() : "(없음)");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return post(url, request, AnalysisResultDto.class);
    }

    /** 분석 이력 목록 조회 */
    public List<AnalysisResultDto> getHistory(int limit) {
        String url = engineUrl + "/api/analyze/history?limit=" + limit;
        log.debug("[EngineClient] GET {}", url);
        ResponseEntity<List<AnalysisResultDto>> res = restTemplate.exchange(
                url, HttpMethod.GET, jsonEntity(null),
                new ParameterizedTypeReference<>() {});
        return res.getBody();
    }

    /* ── 패턴 API ───────────────────────────────────────── */

    public List<FaultPatternDto> getPatterns() {
        String url = engineUrl + "/api/patterns";
        log.debug("[EngineClient] GET {}", url);
        ResponseEntity<List<FaultPatternDto>> res = restTemplate.exchange(
                url, HttpMethod.GET, jsonEntity(null),
                new ParameterizedTypeReference<>() {});
        return res.getBody();
    }

    public FaultPatternDto getPattern(Long id) {
        return restTemplate.getForObject(engineUrl + "/api/patterns/" + id, FaultPatternDto.class);
    }

    public FaultPatternDto createPattern(FaultPatternDto dto) {
        log.info("[EngineClient] 패턴 등록 - name={}", dto.getName());
        return post(engineUrl + "/api/patterns", dto, FaultPatternDto.class);
    }

    public FaultPatternDto updatePattern(Long id, FaultPatternDto dto) {
        log.info("[EngineClient] 패턴 수정 - id={}, name={}", id, dto.getName());
        HttpEntity<FaultPatternDto> entity = new HttpEntity<>(dto, jsonHeaders());
        ResponseEntity<FaultPatternDto> res = restTemplate.exchange(
                engineUrl + "/api/patterns/" + id, HttpMethod.PUT, entity, FaultPatternDto.class);
        return res.getBody();
    }

    public void deletePattern(Long id) {
        log.info("[EngineClient] 패턴 삭제 - id={}", id);
        restTemplate.delete(engineUrl + "/api/patterns/" + id);
    }

    /* ── 공통 유틸 ──────────────────────────────────────── */

    private <T> T post(String url, Object body, Class<T> clazz) {
        HttpEntity<Object> entity = new HttpEntity<>(body, jsonHeaders());
        ResponseEntity<T> res = restTemplate.exchange(url, HttpMethod.POST, entity, clazz);
        return res.getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private <T> HttpEntity<T> jsonEntity(T body) {
        return new HttpEntity<>(body, jsonHeaders());
    }
}
