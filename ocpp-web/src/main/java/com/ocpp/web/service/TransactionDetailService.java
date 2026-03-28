package com.ocpp.web.service;

import com.ocpp.web.dto.OcppFlowEntryDto;
import com.ocpp.web.mapper.TransactionDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * transaction_detail 테이블 서비스 (ocpp-web 자체 DB 직접 조회)
 * ocpp-engine REST 호출 없이 DB에서 직접 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionDetailService {

    private final TransactionDetailMapper transactionDetailMapper;

    /**
     * sessionId로 트랜잭션 상세 목록 조회
     *
     * @param sessionId 분석 세션 ID (예: 20260328-0001)
     * @return transaction_detail 레코드 목록
     */
    public List<OcppFlowEntryDto> getBySessionId(String sessionId) {
        log.debug("[TransactionDetailService] 조회 - sessionId={}", sessionId);
        List<OcppFlowEntryDto> list = transactionDetailMapper.findBySessionId(sessionId);
        log.info("[TransactionDetailService] 조회 완료 - sessionId={}, 건수={}", sessionId, list.size());
        return list;
    }

    /**
     * sessionId에 해당하는 레코드 건수 조회
     *
     * @param sessionId 분석 세션 ID
     * @return 건수
     */
    public int countBySessionId(String sessionId) {
        return transactionDetailMapper.countBySessionId(sessionId);
    }
}
