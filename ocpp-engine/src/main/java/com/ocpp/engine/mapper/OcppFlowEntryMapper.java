package com.ocpp.engine.mapper;

import com.ocpp.engine.dto.OcppFlowEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OcppFlowEntryMapper {
    void insertAll(@Param("list") List<OcppFlowEntry> entries);
    List<OcppFlowEntry> findByAnalysisId(@Param("analysisId") Long analysisId);
}
