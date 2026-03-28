package com.ocpp.engine.mapper;

import com.ocpp.engine.dto.FlowViolation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FlowViolationMapper {
    void insertAll(@Param("list") List<FlowViolation> violations);
    List<FlowViolation> findByAnalysisId(@Param("analysisId") Long analysisId);
}
