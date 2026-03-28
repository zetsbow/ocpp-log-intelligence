package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.FaultPatternDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/pattern")
@RequiredArgsConstructor
public class PatternViewController {

    private final EngineClient engineClient;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("currentMenu", "pattern");
        List<FaultPatternDto> patterns = engineClient.getPatterns();
        model.addAttribute("patterns", patterns);
        return "pattern/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("currentMenu", "pattern");
        model.addAttribute("pattern", new FaultPatternDto());
        model.addAttribute("mode", "new");
        return "pattern/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("currentMenu", "pattern");
        model.addAttribute("pattern", engineClient.getPattern(id));
        model.addAttribute("mode", "edit");
        return "pattern/form";
    }

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

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        engineClient.deletePattern(id);
        return "redirect:/pattern";
    }
}
