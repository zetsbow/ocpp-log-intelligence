package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.AnalyzeRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Controller
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogController {

    private final EngineClient engineClient;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("currentMenu", "log");
        return "log/analyze";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam(required = false) String chargerId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime fromTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime toTime,
            @RequestParam("logFile") MultipartFile logFile,
            Model model) {

        model.addAttribute("currentMenu", "log");
        try {
            String content = new String(logFile.getBytes(), StandardCharsets.UTF_8);

            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setChargerId(chargerId);
            req.setFromTime(fromTime);
            req.setToTime(toTime);
            req.setLogContent(content);
            req.setFileName(logFile.getOriginalFilename());

            AnalysisResultDto result = engineClient.analyzeCharger(req);
            model.addAttribute("result", result);
            model.addAttribute("chargerId", chargerId);

        } catch (Exception e) {
            log.error("로그 분석 실패", e);
            model.addAttribute("error", e.getMessage());
        }
        return "log/result";
    }
}
