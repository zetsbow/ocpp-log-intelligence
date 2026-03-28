package com.ocpp.engine.mapper;

import com.ocpp.engine.dto.AnalysisResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalysisResultMapper {
    void insert(AnalysisResult result);
    List<AnalysisResult> findRecent(@Param("limit") int limit);
    List<AnalysisResult> findAll();
    AnalysisResult findById(@Param("id") Long id);
}
