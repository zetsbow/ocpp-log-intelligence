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

    /** 검색조건 + 페이징 조회 */
    List<OcppFlowEntryDto> findByConditionPaged(
            @Param("sessionId")     String  sessionId,
            @Param("transactionId") String  transactionId,
            @Param("chargerId")     String  chargerId,
            @Param("action")        String  action,
            @Param("direction")     String  direction,
            @Param("isFault")       Integer isFault,
            @Param("offset")        int     offset,
            @Param("size")          int     size);

    /** 검색조건 총 건수 조회 */
    int countByCondition(
            @Param("sessionId")     String  sessionId,
            @Param("transactionId") String  transactionId,
            @Param("chargerId")     String  chargerId,
            @Param("action")        String  action,
            @Param("direction")     String  direction,
            @Param("isFault")       Integer isFault);
}
