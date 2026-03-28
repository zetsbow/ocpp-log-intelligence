package com.ocpp.web.client;

import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.AnalyzeRequestDto;
import com.ocpp.web.dto.FaultPatternDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * ocpp-web → ocpp-engine 내부 REST 통신 클라이언트
 */
@Slf4j
@Component
public class EngineClient {

    private final RestTemplate restTemplate;

    @Value("${engine.url:http://localhost:8081}")
    private String engineUrl;

    public EngineClient() {
        this.restTemplate = new RestTemplate();
    }

    /* ── 분석 API ───────────────────────────────────────── */

    /** 기능2: 특정 충전기·시간 구간 분석 */
    public AnalysisResultDto analyzeCharger(AnalyzeRequestDto request) {
        String url = engineUrl + "/api/analyze/charger";
        log.info("[EngineClient] 충전기 분석 요청: {}", url);
        return post(url, request, AnalysisResultDto.class);
    }

    /** 기능1: 배치 전체 분석 */
    public AnalysisResultDto analyzeBatch(AnalyzeRequestDto request) {
        String url = engineUrl + "/api/analyze/batch";
        log.info("[EngineClient] 배치 분석 요청: {}", url);
        return post(url, request, AnalysisResultDto.class);
    }

    /** 분석 이력 목록 조회 */
    public List<AnalysisResultDto> getHistory(int limit) {
        String url = engineUrl + "/api/analyze/history?limit=" + limit;
        ResponseEntity<List<AnalysisResultDto>> res = restTemplate.exchange(
                url, HttpMethod.GET, jsonEntity(null),
                new ParameterizedTypeReference<>() {});
        return res.getBody();
    }

    /* ── 패턴 API ───────────────────────────────────────── */

    /** 기능3: 전체 패턴 목록 */
    public List<FaultPatternDto> getPatterns() {
        String url = engineUrl + "/api/patterns";
        ResponseEntity<List<FaultPatternDto>> res = restTemplate.exchange(
                url, HttpMethod.GET, jsonEntity(null),
                new ParameterizedTypeReference<>() {});
        return res.getBody();
    }

    /** 기능3: 패턴 단건 조회 */
    public FaultPatternDto getPattern(Long id) {
        return restTemplate.getForObject(engineUrl + "/api/patterns/" + id, FaultPatternDto.class);
    }

    /** 기능3: 패턴 등록 */
    public FaultPatternDto createPattern(FaultPatternDto dto) {
        return post(engineUrl + "/api/patterns", dto, FaultPatternDto.class);
    }

    /** 기능3: 패턴 수정 */
    public FaultPatternDto updatePattern(Long id, FaultPatternDto dto) {
        HttpEntity<FaultPatternDto> entity = new HttpEntity<>(dto, jsonHeaders());
        ResponseEntity<FaultPatternDto> res = restTemplate.exchange(
                engineUrl + "/api/patterns/" + id, HttpMethod.PUT, entity, FaultPatternDto.class);
        return res.getBody();
    }

    /** 기능3: 패턴 삭제 */
    public void deletePattern(Long id) {
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
