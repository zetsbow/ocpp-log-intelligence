package com.ocpp.engine.mapper;

import com.ocpp.engine.dto.FaultDetection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaultDetectionMapper {
    void insertAll(@Param("list") List<FaultDetection> list);
    List<FaultDetection> findByAnalysisId(@Param("analysisId") Long analysisId);
    List<FaultDetection> findByChargerId(@Param("chargerId") String chargerId);
    List<FaultDetection> findRecent(@Param("limit") int limit);
}
