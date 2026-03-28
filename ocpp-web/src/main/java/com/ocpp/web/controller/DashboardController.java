package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.FlowViolationDto;
import com.ocpp.web.dto.OcppFlowEntryDto;
import com.ocpp.web.service.AnalysisResultService;
import com.ocpp.web.service.FlowViolationService;
import com.ocpp.web.service.TransactionDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final EngineClient engineClient;
    private final AnalysisResultService analysisResultService;
    private final TransactionDetailService transactionDetailService;
    private final FlowViolationService flowViolationService;

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
    public String detail(@PathVariable String sessionId,
                         @RequestParam(defaultValue = "1")   int     page,
                         @RequestParam(defaultValue = "20")  int     size,
                         @RequestParam(defaultValue = "")    String  transactionId,
                         @RequestParam(defaultValue = "")    String  chargerId,
                         @RequestParam(defaultValue = "")    String  action,
                         @RequestParam(defaultValue = "")    String  direction,
                         @RequestParam(required = false)     Integer isFault,
                         // 위반 검색 조건
                         @RequestParam(defaultValue = "")    String  vTransactionId,
                         @RequestParam(defaultValue = "")    String  vChargerId,
                         @RequestParam(defaultValue = "")    String  vSeverity,
                         // 활성 탭
                         @RequestParam(defaultValue = "detail") String activeTab,
                         Model model) {
        model.addAttribute("currentMenu",       "dashboard");
        model.addAttribute("sessionId",         sessionId);
        model.addAttribute("activeTab",         activeTab);
        // 트랜잭션 상세 검색 조건
        model.addAttribute("srchTransactionId", transactionId);
        model.addAttribute("srchChargerId",     chargerId);
        model.addAttribute("srchAction",        action);
        model.addAttribute("srchDirection",     direction);
        model.addAttribute("srchIsFault",       isFault);
        model.addAttribute("pageSize",          size);
        // 흐름 위반 검색 조건
        model.addAttribute("vTransactionId",    vTransactionId);
        model.addAttribute("vChargerId",        vChargerId);
        model.addAttribute("vSeverity",         vSeverity);

        // ── 분석 결과 요약 ────────────────────────────────────────
        try {
            AnalysisResultDto result = analysisResultService.getBySessionId(sessionId);
            model.addAttribute("result", result);
        } catch (Exception e) {
            log.error("[DashboardController] 분석 결과 요약 조회 실패 - sessionId={}, error={}", sessionId, e.getMessage(), e);
            model.addAttribute("result", null);
        }

        // ── 트랜잭션 상세 (탭1) ───────────────────────────────────
        try {
            int totalCount  = transactionDetailService.count(sessionId, transactionId, chargerId, action, direction, isFault);
            int totalPages  = (int) Math.ceil((double) totalCount / size);
            int currentPage = Math.max(1, Math.min(page, Math.max(totalPages, 1)));

            List<OcppFlowEntryDto> details = transactionDetailService.search(
                    sessionId, transactionId, chargerId, action, direction, isFault, currentPage, size);

            int pageGroupSize = 10;
            int pageStart = ((currentPage - 1) / pageGroupSize) * pageGroupSize + 1;
            int pageEnd   = Math.min(pageStart + pageGroupSize - 1, Math.max(totalPages, 1));

            model.addAttribute("details",     details);
            model.addAttribute("currentPage", currentPage);
            model.addAttribute("totalPages",  totalPages);
            model.addAttribute("totalCount",  totalCount);
            model.addAttribute("startRow",    (currentPage - 1) * size + 1);
            model.addAttribute("hasPrev",     currentPage > 1);
            model.addAttribute("hasNext",     currentPage < totalPages);
            model.addAttribute("prevPage",    currentPage - 1);
            model.addAttribute("nextPage",    currentPage + 1);
            model.addAttribute("pageStart",   pageStart);
            model.addAttribute("pageEnd",     pageEnd);
            log.info("[DashboardController] 트랜잭션 상세 - sessionId={}, page={}/{}, 건수={}",
                    sessionId, currentPage, totalPages, details.size());
        } catch (Exception e) {
            log.error("[DashboardController] 트랜잭션 상세 조회 실패 - sessionId={}, error={}", sessionId, e.getMessage(), e);
            model.addAttribute("details",    List.of());
            model.addAttribute("totalCount", 0);
            model.addAttribute("totalPages", 0);
        }

        // ── 흐름 위반 (탭2) ───────────────────────────────────────
        try {
            List<FlowViolationDto> violations = flowViolationService.search(
                    sessionId, vTransactionId, vChargerId, vSeverity);
            int violationCount = flowViolationService.countBySessionId(sessionId);
            model.addAttribute("violations",      violations);
            model.addAttribute("violationCount",  violationCount);
            log.info("[DashboardController] 흐름 위반 - sessionId={}, 건수={}", sessionId, violations.size());
        } catch (Exception e) {
            log.error("[DashboardController] 흐름 위반 조회 실패 - sessionId={}, error={}", sessionId, e.getMessage(), e);
            model.addAttribute("violations",     List.of());
            model.addAttribute("violationCount", 0);
        }

        return "dashboard/detail";
    }
}
