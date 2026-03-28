package com.ocpp.engine.controller;

import com.ocpp.engine.dto.OcppFlowEntry;
import com.ocpp.engine.mapper.OcppFlowEntryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transaction-detail")
@RequiredArgsConstructor
public class TransactionDetailController {

    private final OcppFlowEntryMapper ocppFlowEntryMapper;

    /** GET /api/transaction-detail/{sessionId} */
    @GetMapping("/{sessionId}")
    public ResponseEntity<List<OcppFlowEntry>> getBySession(@PathVariable String sessionId) {
        List<OcppFlowEntry> list = ocppFlowEntryMapper.findByAnalysisId(sessionId);
        return ResponseEntity.ok(list);
    }
}
