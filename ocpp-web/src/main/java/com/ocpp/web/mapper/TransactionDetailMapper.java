package com.ocpp.web.mapper;

import com.ocpp.web.dto.OcppFlowEntryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * transaction_detail 테이블 직접 조회 매퍼 (ocpp-web 자체 DB 접근)
 */
@Mapper
public interface TransactionDetailMapper {

    /** sessionId로 트랜잭션 상세 목록 조회 */
    List<OcppFlowEntryDto> findBySessionId(@Param("sessionId") String sessionId);

    /** sessionId에 해당하는 레코드 건수 조회 */
    int countBySessionId(@Param("sessionId") String sessionId);
}
