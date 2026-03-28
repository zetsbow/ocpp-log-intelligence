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
 * [파일명 채번 규칙]
 * 패턴: OCPP-LOG-ANALYSIS-{yyyyMMdd}-{NNNN}.{ext}
 * 예)   OCPP-LOG-ANALYSIS-20260328-0001.txt
 *
 * [sessionId 추출 규칙]
 * 파일명: OCPP-LOG-ANALYSIS-20260328-0001.txt
 *   → prefix 'OCPP-LOG-ANALYSIS-' 제거 + 확장자 제거
 *   → sessionId = "20260328-0001" (CHAR 13자리)
 *   → engine의 AnalyzeRequest.sessionId 에 담아 전달
 *   → analysis_result.session_id(PK) 로 직접 사용
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
        uploadPath = Paths.get(uploadDirConfig)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(uploadPath);
        log.info("[LogController] 업로드 경로 초기화: {}", uploadPath);
    }

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

        if (logFile.isEmpty()) {
            model.addAttribute("error", "파일을 선택해주세요.");
            return "log/analyze";
        }

        try {
            // 1. 채번 규칙으로 파일 저장
            Path savedPath = saveWithSequence(logFile);

            // 2. 저장된 파일명으로부터 sessionId 추출
            //    OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001
            String savedFileName = savedPath.getFileName().toString();
            String sessionId     = extractSessionId(savedFileName);

            // 3. 파일명 URL 인코딩 → fileUrl 생성
            String encodedFileName = URLEncoder.encode(savedFileName, StandardCharsets.UTF_8);
            String fileUrl         = webBaseUrl + "/log/upload/" + encodedFileName;

            log.info("[LogController] 원본 파일명  : {}", logFile.getOriginalFilename());
            log.info("[LogController] 저장 파일명  : {}", savedFileName);
            log.info("[LogController] sessionId   : {}", sessionId);
            log.info("[LogController] fileUrl     : {}", fileUrl);

            // 4. engine 요청 DTO 구성
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setSessionId(sessionId);      // ← 파일명에서 추출한 key
            req.setChargerId(chargerId);
            req.setFromTime(fromTime);
            req.setToTime(toTime);
            req.setFileUrl(fileUrl);
            req.setFileName(logFile.getOriginalFilename());

            // 5. 분석 결과 수신
            AnalysisResultDto result = engineClient.analyzeCharger(req);
            model.addAttribute("result",    result);
            model.addAttribute("savedPath", savedPath.toString());
            model.addAttribute("fileUrl",   "/log/upload/" + encodedFileName);

        } catch (IOException e) {
            log.error("[LogController] 파일 저장 실패", e);
            model.addAttribute("error", "파일 저장 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("[LogController] 분석 요청 실패", e);
            model.addAttribute("error", "분석 실패: " + e.getMessage());
        }
        return "log/result";
    }

    /**
     * 파일명에서 sessionId 추출
     * OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001
     */
    private String extractSessionId(String fileName) {
        String nameWithoutExt = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        if (nameWithoutExt.startsWith(FILE_PREFIX)) {
            return nameWithoutExt.substring(FILE_PREFIX.length()); // 20260328-0001
        }
        return nameWithoutExt;
    }

    /**
     * 채번 규칙으로 파일 저장
     * 패턴: OCPP-LOG-ANALYSIS-{yyyyMMdd}-{NNNN}.{ext}
     */
    private Path saveWithSequence(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "ocpp_log.txt";
        }

        String ext   = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        String today = LocalDate.now().format(DATE_FMT);
        int    nextSeq = findNextSequence(today);

        String savedName = String.format("%s%s-%04d%s", FILE_PREFIX, today, nextSeq, ext);
        Path   savedPath = uploadPath.resolve(savedName);

        Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("[채번] {} (원본: {})", savedName, originalName);
        return savedPath;
    }

    /**
     * 오늘 날짜 기준 다음 시퀀스 번호 계산
     */
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
}
