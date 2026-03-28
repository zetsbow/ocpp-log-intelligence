package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 기능1: 대시보드 — 분석 이력 · 장애 트렌드
 */
@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class DashboardController {

    private final EngineClient engineClient;

    @GetMapping
    public String index(Model model) {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        try {
            List<AnalysisResultDto> history = engineClient.getHistory(30);
            model.addAttribute("history", history);

            // 차트 데이터: 최근 7건 장애 건수
            long totalFaults = history.stream().mapToLong(AnalysisResultDto::getFaultCount).sum();
            long totalMsgs   = history.stream().mapToLong(AnalysisResultDto::getTotalMsgCount).sum();
            model.addAttribute("totalFaults", totalFaults);
            model.addAttribute("totalMsgs",   totalMsgs);
            model.addAttribute("historyCount", history.size());

        } catch (Exception e) {
            log.warn("엔진 연결 실패 - 빈 대시보드 표시: {}", e.getMessage());
            model.addAttribute("engineError", true);
        }
        return "dashboard/index";
    }
}
