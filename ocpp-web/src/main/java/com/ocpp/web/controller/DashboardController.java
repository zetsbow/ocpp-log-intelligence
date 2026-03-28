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

@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class DashboardController {

    private final EngineClient engineClient;

    @GetMapping
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("currentMenu", "dashboard");
        try {
            List<AnalysisResultDto> history = engineClient.getHistory(30);

            long totalTransaction     = history.stream()
                    .mapToLong(AnalysisResultDto::getTotalTransaction).sum();
            long totalFaultTransaction = history.stream()
                    .mapToLong(AnalysisResultDto::getFaultTransactionCount).sum();

            model.addAttribute("history",              history);
            model.addAttribute("totalTransaction",     totalTransaction);
            model.addAttribute("totalFaultTransaction", totalFaultTransaction);
            model.addAttribute("historyCount",         history.size());

        } catch (Exception e) {
            log.warn("엔진 연결 실패 - 빈 대시보드 표시: {}", e.getMessage());
            model.addAttribute("engineError", true);
        }
        return "dashboard/index";
    }
}
