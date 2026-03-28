package com.ocpp.web.service;

import com.ocpp.web.dto.OcppFlowEntryDto;
import com.ocpp.web.mapper.TransactionDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * transaction_detail 테이블 서비스 (ocpp-web 자체 DB 직접 조회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionDetailService {

    private final TransactionDetailMapper transactionDetailMapper;

    /**
     * 검색 조건 + 페이징 조회
     *
     * @param sessionId     분석 세션 ID (필수)
     * @param transactionId 트랜잭션 ID 검색 (완전 일치, 빈 문자열이면 전체)
     * @param chargerId     충전기 ID 검색 (완전 일치, 빈 문자열이면 전체)
     * @param action        액션명 검색 (LIKE, 빈 문자열이면 전체)
     * @param direction     방향 검색 (CP→CS / CS→CP, 빈 문자열이면 전체)
     * @param isFault       장애 여부 (null=전체, 1=장애만, 0=정상만)
     * @param page          페이지 번호 (1-based)
     * @param size          페이지 크기
     */
    public List<OcppFlowEntryDto> search(String sessionId,
                                          String transactionId, String chargerId,
                                          String action, String direction,
                                          Integer isFault, int page, int size) {
        int offset = (page - 1) * size;
        String ti = StringUtils.hasText(transactionId) ? transactionId.trim() : null;
        String ci = StringUtils.hasText(chargerId)     ? chargerId.trim()     : null;
        String ac = StringUtils.hasText(action)        ? action.trim()        : null;
        String di = StringUtils.hasText(direction)     ? direction.trim()     : null;

        log.debug("[TransactionDetailService] 검색 - sessionId={}, transactionId={}, chargerId={}, action={}, direction={}, isFault={}, page={}, size={}",
                sessionId, ti, ci, ac, di, isFault, page, size);

        List<OcppFlowEntryDto> list = transactionDetailMapper.findByConditionPaged(
                sessionId, ti, ci, ac, di, isFault, offset, size);
        log.info("[TransactionDetailService] 검색 완료 - sessionId={}, 건수={}", sessionId, list.size());
        return list;
    }

    /**
     * 검색 조건에 맞는 총 건수 조회
     */
    public int count(String sessionId, String transactionId, String chargerId,
                     String action, String direction, Integer isFault) {
        String ti = StringUtils.hasText(transactionId) ? transactionId.trim() : null;
        String ci = StringUtils.hasText(chargerId)     ? chargerId.trim()     : null;
        String ac = StringUtils.hasText(action)        ? action.trim()        : null;
        String di = StringUtils.hasText(direction)     ? direction.trim()     : null;
        return transactionDetailMapper.countByCondition(sessionId, ti, ci, ac, di, isFault);
    }
}
