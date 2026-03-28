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
import java.time.LocalDateTime;

/**
 * 로그 분석 컨트롤러
 *
 * [파일 전달 방식]
 * - 파일을 서버에 저장 후, engine에 filePath 대신 fileUrl(URL 인코딩된 주소)로 전달
 * - engine은 해당 URL을 HTTP GET으로 호출하여 파일 내용을 읽음
 * - 한글/특수문자 파일명도 URL 인코딩으로 안전하게 처리
 *
 * [채번 규칙]
 * - 동일 파일명 없으면 원본 그대로 저장
 * - 동일 파일명 존재 시 _001, _002 ... _999 시퀀스 부여
 */
@Slf4j
@Controller
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogController {

    private final EngineClient engineClient;

    @Value("${ocpp.log.upload-dir}")
    private String uploadDirConfig;

    /** web 서버 base URL (engine이 파일 다운로드 시 사용) */
    @Value("${ocpp.web.base-url:http://localhost:7777}")
    private String webBaseUrl;

    /** 정규화된 절대 경로 */
    private Path uploadPath;

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

            // 2. 파일명 URL 인코딩
            //    한글/특수문자 파일명 → %EC%A0%95%EC%83%81... 형태로 변환
            String encodedFileName = URLEncoder.encode(
                    savedPath.getFileName().toString(),
                    StandardCharsets.UTF_8
            );

            // 3. fileUrl 생성
            //    예) http://localhost:7777/log/upload/%EC%A0%95%EC%83%81%EC%B6%A9%EC%A0%84%EB%A1%9C%EA%B7%B8.txt
            String fileUrl = webBaseUrl + "/log/upload/" + encodedFileName;

            log.info("[LogController] 저장 파일명  : {}", savedPath.getFileName());
            log.info("[LogController] 저장 경로    : {}", savedPath);
            log.info("[LogController] engine 전달 URL: {}", fileUrl);

            // 4. engine에 fileUrl로 전달 (filePath 미사용)
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setChargerId(chargerId);
            req.setFromTime(fromTime);
            req.setToTime(toTime);
            req.setFileUrl(fileUrl);
            req.setFileName(savedPath.getFileName().toString()); // 화면 표시용 원본 파일명

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
     * 채번 규칙으로 파일 저장
     * 1. 원본 파일명으로 저장 시도
     * 2. 이미 존재하면 _001, _002 ... _999 시퀀스 부여
     */
    private Path saveWithSequence(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "ocpp_log.txt";
        }

        int dotIdx  = originalName.lastIndexOf('.');
        String base = dotIdx > 0 ? originalName.substring(0, dotIdx) : originalName;
        String ext  = dotIdx > 0 ? originalName.substring(dotIdx)    : "";

        // 원본 파일명 우선
        Path candidate = uploadPath.resolve(originalName);
        if (!Files.exists(candidate)) {
            Files.copy(file.getInputStream(), candidate, StandardCopyOption.REPLACE_EXISTING);
            log.info("[채번] 원본 사용: {}", candidate.getFileName());
            return candidate;
        }

        // 시퀀스 부여
        for (int seq = 1; seq <= 999; seq++) {
            String seqName = String.format("%s_%03d%s", base, seq, ext);
            candidate = uploadPath.resolve(seqName);
            if (!Files.exists(candidate)) {
                Files.copy(file.getInputStream(), candidate, StandardCopyOption.REPLACE_EXISTING);
                log.info("[채번] 시퀀스 사용: {} (원본: {})", candidate.getFileName(), originalName);
                return candidate;
            }
        }

        throw new IOException("파일명 채번 한도 초과(999): " + originalName);
    }
}
