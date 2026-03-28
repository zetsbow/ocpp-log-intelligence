package com.ocpp.web.controller;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.AnalyzeRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 기능2: 로그 파일 업로드 → 서버 경로 저장 → engine 분석 요청
 */
@Slf4j
@Controller
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogController {

    private final EngineClient engineClient;

    @Value("${ocpp.log.upload-dir}")
    private String uploadDir;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("currentMenu", "log");
        return "log/analyze";
    }

    /**
     * 1) 업로드 파일을 서버 경로에 저장
     * 2) engine에 파일 경로 + 필터 조건 전달 → 분석 결과 반환
     */
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
            // 1. 업로드 디렉토리 생성 (없으면 자동 생성)
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);

            // 2. 파일명 중복 방지: 타임스탬프 prefix
            String originalName = logFile.getOriginalFilename();
            String timestamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String savedName    = timestamp + "_" + originalName;
            Path   savedPath    = uploadPath.resolve(savedName);

            // 3. 서버 경로에 저장
            Files.copy(logFile.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("[LogController] 파일 저장 완료: {}", savedPath.toAbsolutePath());

            // 4. engine에 파일 경로 + 조건 전달 (파일 내용 미전송)
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setChargerId(chargerId);
            req.setFromTime(fromTime);
            req.setToTime(toTime);
            req.setFilePath(savedPath.toAbsolutePath().toString());
            req.setFileName(originalName);

            AnalysisResultDto result = engineClient.analyzeCharger(req);
            model.addAttribute("result",    result);
            model.addAttribute("savedPath", savedPath.toAbsolutePath().toString());

        } catch (IOException e) {
            log.error("[LogController] 파일 저장 실패", e);
            model.addAttribute("error", "파일 저장 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("[LogController] 분석 요청 실패", e);
            model.addAttribute("error", "분석 실패: " + e.getMessage());
        }
        return "log/result";
    }
}
