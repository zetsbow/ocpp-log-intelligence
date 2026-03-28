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

    @Value("${engine.url:http://127.0.0.1:7778}")
    private String engineUrl;

    public EngineClient() {
        this.restTemplate = new RestTemplate();
    }

    /* ── 분석 API ───────────────────────────────────────── */

    public AnalysisResultDto analyzeCharger(AnalyzeRequestDto request) {
        String url = engineUrl + "/api/analyze/charger";

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("[EngineClient] POST {}", url);
        log.info("[EngineClient] 요청 파라미터");
        log.info("  ├─ chargerId  : {}", isBlank(request.getChargerId()) ? "(전체 분석)" : request.getChargerId());
        log.info("  ├─ fromTime   : {}", request.getFromTime() != null   ? request.getFromTime()  : "(제한 없음)");
        log.info("  ├─ toTime     : {}", request.getToTime()   != null   ? request.getToTime()    : "(제한 없음)");
        log.info("  ├─ fileName   : {}", isBlank(request.getFileName())  ? "(없음)" : request.getFileName());
        log.info("  ├─ fileUrl    : {}", isBlank(request.getFileUrl())   ? "(없음)" : request.getFileUrl());
        log.info("  └─ logContent : {}", request.getLogContent() != null
                ? request.getLogContent().length() + " bytes" : "(없음)");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            AnalysisResultDto result = post(url, request, AnalysisResultDto.class);
            log.info("[EngineClient] 응답 수신 - sessionId={}, totalTransaction={}, faultTransactionCount={}",
                    result != null ? result.getSessionId()             : "null",
                    result != null ? result.getTotalTransaction()      : "null",
                    result != null ? result.getFaultTransactionCount() : "null");
            return result;
        } catch (RestClientException e) {
            log.error("[EngineClient] engine 호출 실패 - url={}, error={}", url, e.getMessage());
            throw e;
        }
    }

    public AnalysisResultDto analyzeBatch(AnalyzeRequestDto request) {
        String url = engineUrl + "/api/analyze/batch";

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("[EngineClient] POST {} (배치)", url);
        log.info("  ├─ fileName : {}", isBlank(request.getFileName()) ? "(없음)" : request.getFileName());
        log.info("  └─ fileUrl  : {}", isBlank(request.getFileUrl())  ? "(없음)" : request.getFileUrl());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            AnalysisResultDto result = post(url, request, AnalysisResultDto.class);
            log.info("[EngineClient] 배치 응답 수신 - sessionId={}, totalTransaction={}, faultTransactionCount={}",
                    result != null ? result.getSessionId()             : "null",
                    result != null ? result.getTotalTransaction()      : "null",
                    result != null ? result.getFaultTransactionCount() : "null");
            return result;
        } catch (RestClientException e) {
            log.error("[EngineClient] 배치 engine 호출 실패 - url={}, error={}", url, e.getMessage());
            throw e;
        }
    }

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
        ResponseEntity<List<FaultPatternDto>> res = restTemplate.exchange(
                engineUrl + "/api/patterns", HttpMethod.GET, jsonEntity(null),
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
