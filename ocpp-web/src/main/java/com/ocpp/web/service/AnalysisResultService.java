package com.ocpp.web.service;

import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.mapper.AnalysisResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * analysis_result 테이블 서비스 (ocpp-web 자체 DB 직접 조회)
 * ocpp-engine REST 호출 없이 DB에서 직접 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisResultService {

    private final AnalysisResultMapper analysisResultMapper;

    /**
     * sessionId로 분석 결과 단건 조회
     *
     * @param sessionId 분석 세션 ID (예: 20260328-0001)
     * @return AnalysisResultDto 또는 null
     */
    public AnalysisResultDto getBySessionId(String sessionId) {
        log.debug("[AnalysisResultService] 단건 조회 - sessionId={}", sessionId);
        AnalysisResultDto result = analysisResultMapper.findBySessionId(sessionId);
        log.info("[AnalysisResultService] 단건 조회 완료 - sessionId={}, found={}", sessionId, result != null);
        return result;
    }

    /**
     * 최근 분석 이력 목록 조회
     *
     * @param limit 조회 건수
     * @return AnalysisResultDto 목록
     */
    public List<AnalysisResultDto> getRecent(int limit) {
        log.debug("[AnalysisResultService] 최근 이력 조회 - limit={}", limit);
        List<AnalysisResultDto> list = analysisResultMapper.findRecent(limit);
        log.info("[AnalysisResultService] 최근 이력 조회 완료 - 건수={}", list.size());
        return list;
    }
}
