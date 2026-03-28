package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.FaultPatternDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 기능3: 장애 패턴 등록·관리 화면
 */
@Slf4j
@Controller
@RequestMapping("/pattern")
@RequiredArgsConstructor
public class PatternViewController {

    private final EngineClient engineClient;

    /** 패턴 목록 */
    @GetMapping
    public String list(Model model) {
        List<FaultPatternDto> patterns = engineClient.getPatterns();
        model.addAttribute("patterns", patterns);
        return "pattern/list";
    }

    /** 패턴 등록 폼 */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("pattern", new FaultPatternDto());
        model.addAttribute("mode", "new");
        return "pattern/form";
    }

    /** 패턴 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        FaultPatternDto pattern = engineClient.getPattern(id);
        model.addAttribute("pattern", pattern);
        model.addAttribute("mode", "edit");
        return "pattern/form";
    }

    /** 패턴 저장 (등록 / 수정) */
    @PostMapping("/save")
    public String save(@ModelAttribute FaultPatternDto pattern) {
        if (pattern.getId() == null) {
            engineClient.createPattern(pattern);
            log.info("[기능3] 패턴 등록: {}", pattern.getName());
        } else {
            engineClient.updatePattern(pattern.getId(), pattern);
            log.info("[기능3] 패턴 수정: id={}", pattern.getId());
        }
        return "redirect:/pattern";
    }

    /** 패턴 삭제 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        engineClient.deletePattern(id);
        return "redirect:/pattern";
    }
}
