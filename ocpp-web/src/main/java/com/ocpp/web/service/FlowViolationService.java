package com.ocpp.web.service;

import com.ocpp.web.dto.FlowViolationDto;
import com.ocpp.web.mapper.FlowViolationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * flow_violation 테이블 서비스 (ocpp-web 자체 DB 직접 조회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowViolationService {

    private final FlowViolationMapper flowViolationMapper;

    /**
     * sessionId로 흐름 위반 전체 목록 조회
     */
    public List<FlowViolationDto> getBySessionId(String sessionId) {
        log.debug("[FlowViolationService] 전체 조회 - sessionId={}", sessionId);
        List<FlowViolationDto> list = flowViolationMapper.findBySessionId(sessionId);
        log.info("[FlowViolationService] 조회 완료 - sessionId={}, 건수={}", sessionId, list.size());
        return list;
    }

    /**
     * 검색 조건으로 흐름 위반 목록 조회
     *
     * @param sessionId     분석 세션 ID (필수)
     * @param transactionId 트랜잭션 ID (빈 문자열이면 전체)
     * @param chargerId     충전기 ID (빈 문자열이면 전체)
     * @param severity      심각도 ERROR / WARN (빈 문자열이면 전체)
     */
    public List<FlowViolationDto> search(String sessionId,
                                          String transactionId, String chargerId, String severity) {
        String ti = StringUtils.hasText(transactionId) ? transactionId.trim() : null;
        String ci = StringUtils.hasText(chargerId)     ? chargerId.trim()     : null;
        String sv = StringUtils.hasText(severity)      ? severity.trim()      : null;
        log.debug("[FlowViolationService] 검색 - sessionId={}, transactionId={}, chargerId={}, severity={}",
                sessionId, ti, ci, sv);
        List<FlowViolationDto> list = flowViolationMapper.findByCondition(sessionId, ti, ci, sv);
        log.info("[FlowViolationService] 검색 완료 - sessionId={}, 건수={}", sessionId, list.size());
        return list;
    }

    /**
     * sessionId에 해당하는 총 건수
     */
    public int countBySessionId(String sessionId) {
        return flowViolationMapper.countBySessionId(sessionId);
    }
}
