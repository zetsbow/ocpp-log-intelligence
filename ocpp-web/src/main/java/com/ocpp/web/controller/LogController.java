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

/**
 * 로그 분석 컨트롤러
 *
 * 파일 저장 규칙:
 *   - 원본 파일명 그대로 저장 → URL 예측 가능
 *   - 예) OCPP16_Log-2026-03-26.0.txt
 *   - http://localhost:7777/log/upload/OCPP16_Log-2026-03-26.0.txt 로 바로 접근 가능
 *   - 동일 파일명이 이미 존재하면 덮어씀 (재업로드 가능)
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
            // 1. 원본 파일명 그대로 저장
            Path savedPath = saveUploadedFile(logFile);

            log.info("[LogController] 파일 저장 완료: {}", savedPath);
            log.info("[LogController] 파일 URL: http://localhost:7777/log/upload/{}",
                    savedPath.getFileName());

            // 2. engine에 파일 경로 + 필터 조건 전달
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setChargerId(chargerId);
            req.setFromTime(fromTime);
            req.setToTime(toTime);
            req.setFilePath(savedPath.toAbsolutePath().toString());
            req.setFileName(logFile.getOriginalFilename());

            // 3. 분석 결과 수신
            AnalysisResultDto result = engineClient.analyzeCharger(req);
            model.addAttribute("result",    result);
            model.addAttribute("savedPath", savedPath.toAbsolutePath().toString());
            model.addAttribute("fileUrl",
                    "/log/upload/" + savedPath.getFileName().toString());

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
     * 업로드 파일 저장
     * - 원본 파일명 그대로 사용 → URL 예측 가능
     * - REPLACE_EXISTING: 동일 파일 재업로드 시 덮어씀
     */
    private Path saveUploadedFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        // 원본 파일명 그대로 사용
        String originalName = file.getOriginalFilename();
        Path savedPath = uploadPath.resolve(originalName);

        Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
        return savedPath;
    }
}
