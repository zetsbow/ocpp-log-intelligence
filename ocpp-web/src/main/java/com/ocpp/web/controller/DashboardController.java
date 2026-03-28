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
import org.springframework.web.bind.annotation.RequestParam;

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
    public String detail(@PathVariable String sessionId,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
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

        // 트랜잭션 상세 — 페이징 조회
        try {
            int totalCount  = transactionDetailService.countBySessionId(sessionId);
            int totalPages  = (int) Math.ceil((double) totalCount / size);
            int currentPage = Math.max(1, Math.min(page, Math.max(totalPages, 1)));

            List<OcppFlowEntryDto> details = transactionDetailService.getBySessionIdPaged(sessionId, currentPage, size);
            log.info("[DashboardController] 트랜잭션 상세 조회 완료 - sessionId={}, page={}/{}, 건수={}",
                    sessionId, currentPage, totalPages, details.size());

            int startRow = (currentPage - 1) * size + 1;  // 현재 페이지 시작 행 번호

            model.addAttribute("details",      details);
            model.addAttribute("currentPage",  currentPage);
            model.addAttribute("totalPages",   totalPages);
            model.addAttribute("totalCount",   totalCount);
            model.addAttribute("pageSize",     size);
            model.addAttribute("startRow",     startRow);
            model.addAttribute("hasPrev",      currentPage > 1);
            model.addAttribute("hasNext",      currentPage < totalPages);
            model.addAttribute("prevPage",     currentPage - 1);
            model.addAttribute("nextPage",     currentPage + 1);

            // 페이지 그룹 (최대 10개 표시)
            int pageGroupSize = 10;
            int pageStart = ((currentPage - 1) / pageGroupSize) * pageGroupSize + 1;
            int pageEnd   = Math.min(pageStart + pageGroupSize - 1, totalPages);
            model.addAttribute("pageStart", pageStart);
            model.addAttribute("pageEnd",   pageEnd);

        } catch (Exception e) {
            log.error("[DashboardController] 트랜잭션 상세 조회 실패 - sessionId={}, error={}", sessionId, e.getMessage(), e);
            model.addAttribute("details",    List.of());
            model.addAttribute("totalCount", 0);
            model.addAttribute("totalPages", 0);
        }

        return "dashboard/detail";
    }
}
