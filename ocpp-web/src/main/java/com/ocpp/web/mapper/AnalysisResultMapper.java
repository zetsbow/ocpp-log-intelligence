package com.ocpp.web.mapper;

import com.ocpp.web.dto.AnalysisResultDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * analysis_result 테이블 직접 조회 매퍼 (ocpp-web 자체 DB 접근)
 */
@Mapper
public interface AnalysisResultMapper {

    /** sessionId로 분석 결과 단건 조회 */
    AnalysisResultDto findBySessionId(@Param("sessionId") String sessionId);

    /** 최근 분석 이력 목록 조회 */
    List<AnalysisResultDto> findRecent(@Param("limit") int limit);
}
