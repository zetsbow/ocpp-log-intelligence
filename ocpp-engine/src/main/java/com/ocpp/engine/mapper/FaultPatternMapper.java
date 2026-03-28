package com.ocpp.engine.mapper;

import com.ocpp.engine.dto.FaultPattern;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaultPatternMapper {
    List<FaultPattern> findAll();
    List<FaultPattern> findAllEnabled();
    FaultPattern findById(@Param("id") Long id);
    void insert(FaultPattern pattern);
    void update(FaultPattern pattern);
    void delete(@Param("id") Long id);
    void updateEnabled(@Param("id") Long id, @Param("enabled") int enabled);
}
