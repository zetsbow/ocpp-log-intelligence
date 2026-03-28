package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.AnalyzeRequestDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 로그 분석 컨트롤러
 *
 * [PRG 패턴 적용]
 * POST /log/analyze  → 파일 저장 + engine 분석 → redirect:/log/result/{sessionId}
 * GET  /log/result/{sessionId} → 결과 조회 (새로고침 가능)
 *
 * [파일명 채번 규칙]
 * OCPP-LOG-ANALYSIS-{yyyyMMdd}-{NNNN}.{ext}
 * 예) OCPP-LOG-ANALYSIS-20260328-0001.txt
 *
 * [sessionId 추출]
 * OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001 (CHAR 13자리)
 */
@Slf4j
@Controller
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogController {

    private final EngineClient engineClient;

    @Value("${ocpp.log.upload-dir}")
    private String uploadDirConfig;

    @Value("${ocpp.web.base-url:http://127.0.0.1:7777}")
    private String webBaseUrl;

    private Path uploadPath;

    private static final String FILE_PREFIX = "OCPP-LOG-ANALYSIS-";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @PostConstruct
    public void init() throws IOException {
        uploadPath = Paths.get(uploadDirConfig).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        log.info("[LogController] 업로드 경로 초기화: {}", uploadPath);
    }

    /* ── 업로드 폼 ────────────────────────────────────── */

    @GetMapping
    public String form(Model model) {
        model.addAttribute("currentMenu", "log");
        return "log/analyze";
    }

    /* ── 파일 업로드 + 분석 (POST → Redirect) ───────────
     * PRG 패턴: 분석 완료 후 GET /log/result/{sessionId} 로 redirect
     * → 사용자가 새로고침해도 GET 요청만 발생하므로 중복 분석 없음
     * ─────────────────────────────────────────────────── */
    @PostMapping("/analyze")
    public String analyze(
            @RequestParam(required = false) String chargerId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime fromTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime toTime,
            @RequestParam("logFile") MultipartFile logFile,
            RedirectAttributes redirectAttributes) {

        if (logFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "파일을 선택해주세요.");
            return "redirect:/log";
        }

        try {
            // 1. 채번 규칙으로 파일 저장
            Path savedPath = saveWithSequence(logFile);

            // 2. sessionId 추출 (파일명 → 20260328-0001)
            String savedFileName = savedPath.getFileName().toString();
            String sessionId     = extractSessionId(savedFileName);

            // 3. fileUrl 생성 (URL 인코딩)
            String encodedFileName = URLEncoder.encode(savedFileName, StandardCharsets.UTF_8);
            String fileUrl         = webBaseUrl + "/log/upload/" + encodedFileName;

            log.info("[LogController] 원본 파일명  : {}", logFile.getOriginalFilename());
            log.info("[LogController] 저장 파일명  : {}", savedFileName);
            log.info("[LogController] sessionId   : {}", sessionId);
            log.info("[LogController] fileUrl     : {}", fileUrl);

            // 4. engine 분석 요청
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setSessionId(sessionId);
            req.setChargerId(chargerId);
            req.setFromTime(fromTime);
            req.setToTime(toTime);
            req.setFileUrl(fileUrl);
            req.setFileName(logFile.getOriginalFilename());

            engineClient.analyzeCharger(req);

            // 5. PRG: GET /log/result/{sessionId} 로 redirect
            return "redirect:/log/result/" + sessionId;

        } catch (IOException e) {
            log.error("[LogController] 파일 저장 실패", e);
            redirectAttributes.addFlashAttribute("error", "파일 저장 실패: " + e.getMessage());
            return "redirect:/log";
        } catch (Exception e) {
            log.error("[LogController] 분석 요청 실패", e);
            redirectAttributes.addFlashAttribute("error", "분석 실패: " + e.getMessage());
            return "redirect:/log";
        }
    }

    /* ── 결과 조회 (GET — 새로고침 가능) ─────────────────
     * sessionId로 engine에서 분석 결과를 직접 조회
     * → URL을 북마크하거나 새로고침해도 항상 동일한 결과 표시
     * ─────────────────────────────────────────────────── */
    @GetMapping("/result/{sessionId}")
    public String result(@PathVariable String sessionId, Model model) {
        model.addAttribute("currentMenu", "log");
        try {
            AnalysisResultDto result = engineClient.getResultBySessionId(sessionId);
            if (result == null) {
                model.addAttribute("error", "분석 결과를 찾을 수 없습니다. sessionId: " + sessionId);
                return "log/result";
            }

            // 파일 URL 재구성 (결과 화면에 링크 표시용)
            String encodedFileName = URLEncoder.encode(result.getFileName() != null
                    ? FILE_PREFIX + sessionId + getExtFromFileName(result.getFileName())
                    : FILE_PREFIX + sessionId + ".txt", StandardCharsets.UTF_8);

            model.addAttribute("result",  result);
            model.addAttribute("fileUrl", "/log/upload/" + encodedFileName);

        } catch (Exception e) {
            log.error("[LogController] 결과 조회 실패 - sessionId={}", sessionId, e);
            model.addAttribute("error", "결과 조회 실패: " + e.getMessage());
        }
        return "log/result";
    }

    /* ── 유틸 메서드 ──────────────────────────────────── */

    /** 파일명 채번 규칙으로 저장 */
    private Path saveWithSequence(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) originalName = "ocpp_log.txt";

        String ext    = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String today  = LocalDate.now().format(DATE_FMT);
        int    seq    = findNextSequence(today);

        String savedName = String.format("%s%s-%04d%s", FILE_PREFIX, today, seq, ext);
        Path   savedPath = uploadPath.resolve(savedName);

        Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("[채번] {} (원본: {})", savedName, originalName);
        return savedPath;
    }

    /** 오늘 날짜 기준 다음 시퀀스 번호 계산 */
    private int findNextSequence(String today) throws IOException {
        String todayPrefix = FILE_PREFIX + today + "-";
        AtomicInteger maxSeq = new AtomicInteger(0);
        try (Stream<Path> stream = Files.list(uploadPath)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(todayPrefix))
                    .forEach(name -> {
                        try {
                            String afterPrefix = name.substring(todayPrefix.length());
                            String seqStr = afterPrefix.contains(".")
                                    ? afterPrefix.substring(0, afterPrefix.indexOf('.'))
                                    : afterPrefix;
                            int seq = Integer.parseInt(seqStr);
                            if (seq > maxSeq.get()) maxSeq.set(seq);
                        } catch (NumberFormatException ignored) {}
                    });
        }
        return maxSeq.get() + 1;
    }

    /** 파일명에서 sessionId 추출: OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001 */
    private String extractSessionId(String fileName) {
        String nameWithoutExt = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        return nameWithoutExt.startsWith(FILE_PREFIX)
                ? nameWithoutExt.substring(FILE_PREFIX.length())
                : nameWithoutExt;
    }

    /** 원본 파일명에서 확장자 추출: sample.txt → .txt */
    private String getExtFromFileName(String fileName) {
        return fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.'))
                : ".txt";
    }
}
