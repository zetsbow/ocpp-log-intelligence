package com.ocpp.engine.service;

import com.ocpp.engine.dto.FaultPattern;
import com.ocpp.engine.mapper.FaultPatternMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 기능3: 장애 패턴 CRUD 서비스
 */
@Service
@RequiredArgsConstructor
public class FaultPatternService {

    private final FaultPatternMapper patternMapper;

    public List<FaultPattern> findAll() {
        return patternMapper.findAll();
    }

    public FaultPattern findById(Long id) {
        return patternMapper.findById(id);
    }

    public void save(FaultPattern pattern) {
        if (pattern.getId() == null) {
            patternMapper.insert(pattern);
        } else {
            patternMapper.update(pattern);
        }
    }

    public void delete(Long id) {
        patternMapper.delete(id);
    }

    public void toggleEnabled(Long id, int enabled) {
        patternMapper.updateEnabled(id, enabled);
    }
}
