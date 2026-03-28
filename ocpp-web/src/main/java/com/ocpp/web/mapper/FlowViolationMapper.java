package com.ocpp.web.mapper;

import com.ocpp.web.dto.FlowViolationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * flow_violation 테이블 직접 조회 매퍼 (ocpp-web 자체 DB 접근)
 */
@Mapper
public interface FlowViolationMapper {

    /** sessionId로 흐름 위반 목록 전체 조회 */
    List<FlowViolationDto> findBySessionId(@Param("sessionId") String sessionId);

    /** sessionId + 검색조건 조회 */
    List<FlowViolationDto> findByCondition(
            @Param("sessionId")     String sessionId,
            @Param("transactionId") String transactionId,
            @Param("chargerId")     String chargerId,
            @Param("severity")      String severity);

    /** sessionId 총 건수 */
    int countBySessionId(@Param("sessionId") String sessionId);
}
