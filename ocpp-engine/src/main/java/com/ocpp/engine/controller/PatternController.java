package com.ocpp.engine.controller;

import com.ocpp.engine.dto.FaultPattern;
import com.ocpp.engine.service.FaultPatternService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 기능3: 장애 패턴 CRUD REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/patterns")
@RequiredArgsConstructor
public class PatternController {

    private final FaultPatternService patternService;

    /** 전체 패턴 목록 */
    @GetMapping
    public ResponseEntity<List<FaultPattern>> findAll() {
        return ResponseEntity.ok(patternService.findAll());
    }

    /** 패턴 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<FaultPattern> findById(@PathVariable Long id) {
        FaultPattern p = patternService.findById(id);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(p);
    }

    /** 패턴 등록 */
    @PostMapping
    public ResponseEntity<FaultPattern> create(@RequestBody FaultPattern pattern) {
        patternService.save(pattern);
        log.info("[기능3] 패턴 등록 - name={}", pattern.getName());
        return ResponseEntity.ok(pattern);
    }

    /** 패턴 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<FaultPattern> update(@PathVariable Long id,
                                                @RequestBody FaultPattern pattern) {
        pattern.setId(id);
        patternService.save(pattern);
        return ResponseEntity.ok(pattern);
    }

    /** 패턴 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patternService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 활성화/비활성화 토글 */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggle(@PathVariable Long id,
                                       @RequestBody Map<String, Integer> body) {
        patternService.toggleEnabled(id, body.getOrDefault("enabled", 1));
        return ResponseEntity.ok().build();
    }
}
