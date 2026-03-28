package com.ocpp.web.service;

import com.ocpp.web.dto.OcppFlowEntryDto;
import com.ocpp.web.mapper.TransactionDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * transaction_detail 테이블 서비스 (ocpp-web 자체 DB 직접 조회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionDetailService {

    private final TransactionDetailMapper transactionDetailMapper;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * sessionId로 트랜잭션 상세 목록 페이징 조회
     *
     * @param sessionId 분석 세션 ID
     * @param page      페이지 번호 (1-based)
     * @param size      페이지 크기
     * @return 페이지 데이터 목록
     */
    public List<OcppFlowEntryDto> getBySessionIdPaged(String sessionId, int page, int size) {
        int offset = (page - 1) * size;
        log.debug("[TransactionDetailService] 페이징 조회 - sessionId={}, page={}, size={}, offset={}", sessionId, page, size, offset);
        List<OcppFlowEntryDto> list = transactionDetailMapper.findBySessionIdPaged(sessionId, offset, size);
        log.info("[TransactionDetailService] 페이징 조회 완료 - sessionId={}, 건수={}", sessionId, list.size());
        return list;
    }

    /**
     * sessionId에 해당하는 총 레코드 건수 조회
     */
    public int countBySessionId(String sessionId) {
        return transactionDetailMapper.countBySessionId(sessionId);
    }

    /**
     * 전체 페이지 수 계산
     */
    public int getTotalPages(String sessionId, int size) {
        int total = countBySessionId(sessionId);
        return (int) Math.ceil((double) total / size);
    }

    public int getDefaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }
}
