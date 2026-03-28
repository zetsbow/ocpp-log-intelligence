package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.OcppFlowEntryDto;
import com.ocpp.web.service.AnalysisResultService;
import com.ocpp.web.service.TransactionDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final EngineClient engineClient;
    private final AnalysisResultService analysisResultService;
    private final TransactionDetailService transactionDetailService;

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("currentMenu", "dashboard");
        try {
            List<AnalysisResultDto> history = engineClient.getHistory(30);

            long totalTransaction      = history.stream()
                    .mapToLong(AnalysisResultDto::getTotalTransaction).sum();
            long totalFaultTransaction = history.stream()
                    .mapToLong(AnalysisResultDto::getFaultTransactionCount).sum();

            model.addAttribute("history",               history);
            model.addAttribute("totalTransaction",      totalTransaction);
            model.addAttribute("totalFaultTransaction", totalFaultTransaction);
            model.addAttribute("historyCount",          history.size());

        } catch (Exception e) {
            log.warn("엔진 연결 실패 - 빈 대시보드 표시: {}", e.getMessage());
            model.addAttribute("engineError", true);
        }
        return "dashboard/index";
    }

    @GetMapping("/dashboard/detail/{sessionId}")
    public String detail(@PathVariable String sessionId, Model model) {
        model.addAttribute("currentMenu", "dashboard");
        model.addAttribute("sessionId", sessionId);

        // 분석 결과 요약 — ocpp-web 자체 DB 직접 조회
        try {
            AnalysisResultDto result = analysisResultService.getBySessionId(sessionId);
            model.addAttribute("result", result);
        } catch (Exception e) {
            log.error("[DashboardController] 분석 결과 요약 조회 실패 - sessionId={}, error={}", sessionId, e.getMessage(), e);
            model.addAttribute("result", null);
        }

        // 트랜잭션 상세 — ocpp-web 자체 DB 직접 조회 (engine과 무관하게 독립 실행)
        try {
            List<OcppFlowEntryDto> details = transactionDetailService.getBySessionId(sessionId);
            log.info("[DashboardController] 트랜잭션 상세 조회 완료 - sessionId={}, 건수={}", sessionId, details.size());
            model.addAttribute("details", details);
        } catch (Exception e) {
            log.error("[DashboardController] 트랜잭션 상세 조회 실패 - sessionId={}, error={}", sessionId, e.getMessage(), e);
            model.addAttribute("details", List.of());
        }

        return "dashboard/detail";
    }
}
