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
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 로그 분석 컨트롤러
 *
 * [파일명 채번 규칙]
 * 패턴: OCPP-LOG-ANALYSIS-yyyyMMdd-NNNN
 * 예)   OCPP-LOG-ANALYSIS-20260328-0001.txt
 *       OCPP-LOG-ANALYSIS-20260328-0002.txt
 *
 * - 날짜가 바뀌면 시퀀스 0001부터 재시작
 * - 날짜 내에서 이미 존재하는 최대 시퀀스 + 1로 채번
 * - 원본 파일명은 fileName 필드로 별도 보관 (화면 표시용)
 * - 확장자는 원본 파일 확장자 유지
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

    private static final String FILE_PREFIX    = "OCPP-LOG-ANALYSIS";
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
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") java.time.LocalDateTime fromTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") java.time.LocalDateTime toTime,
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

            // 2. 파일명 URL 인코딩 (영문이므로 인코딩 변화 없음, 안전하게 적용)
            String encodedFileName = URLEncoder.encode(
                    savedPath.getFileName().toString(), StandardCharsets.UTF_8);

            // 3. engine 전달용 fileUrl 생성
            String fileUrl = webBaseUrl + "/log/upload/" + encodedFileName;

            log.info("[LogController] 원본 파일명  : {}", logFile.getOriginalFilename());
            log.info("[LogController] 저장 파일명  : {}", savedPath.getFileName());
            log.info("[LogController] 저장 경로    : {}", savedPath);
            log.info("[LogController] engine URL  : {}", fileUrl);

            // 4. engine 요청 DTO 구성
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setChargerId(chargerId);
            req.setFromTime(fromTime);
            req.setToTime(toTime);
            req.setFileUrl(fileUrl);
            req.setFileName(logFile.getOriginalFilename()); // 화면 표시용 원본 파일명

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
     *
     * [패턴] OCPP-LOG-ANALYSIS-{yyyyMMdd}-{NNNN}.{ext}
     *
     * 1. 오늘 날짜로 업로드 디렉토리에서 기존 파일 중
     *    같은 날짜 패턴의 최대 시퀀스 번호를 조회
     * 2. 최대 시퀀스 + 1 로 파일명 결정
     * 3. 날짜가 바뀌면 0001부터 재시작
     *
     * 예) 오늘 이미 0003까지 있으면 → 0004로 저장
     *     날짜 변경 시 → 0001부터 재시작
     */
    private Path saveWithSequence(MultipartFile file) throws IOException {

        // 원본 확장자 추출
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));  // 예) .txt .log
        }

        // 오늘 날짜 문자열
        String today = LocalDate.now().format(DATE_FMT);  // 예) 20260328

        // 오늘 날짜로 이미 존재하는 최대 시퀀스 조회
        int nextSeq = findNextSequence(today);

        // 파일명 생성: OCPP-LOG-ANALYSIS-20260328-0001.txt
        String savedName = String.format("%s-%s-%04d%s", FILE_PREFIX, today, nextSeq, ext);
        Path savedPath = uploadPath.resolve(savedName);

        Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("[채번] 저장 파일명: {} (원본: {})", savedName, originalName);

        return savedPath;
    }

    /**
     * 오늘 날짜 기준 다음 시퀀스 번호 계산
     *
     * 예) OCPP-LOG-ANALYSIS-20260328-0001.txt 존재 → 2 반환
     *     OCPP-LOG-ANALYSIS-20260328-0003.txt 가 최대 → 4 반환
     *     오늘 날짜 파일 없음 → 1 반환
     */
    private int findNextSequence(String today) throws IOException {

        // 오늘 날짜 prefix: OCPP-LOG-ANALYSIS-20260328-
        String todayPrefix = FILE_PREFIX + "-" + today + "-";

        AtomicInteger maxSeq = new AtomicInteger(0);

        try (Stream<Path> stream = Files.list(uploadPath)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(todayPrefix))
                    .forEach(name -> {
                        try {
                            // OCPP-LOG-ANALYSIS-20260328-0003.txt 에서 0003 추출
                            String afterPrefix = name.substring(todayPrefix.length()); // 0003.txt
                            String seqStr = afterPrefix.contains(".")
                                    ? afterPrefix.substring(0, afterPrefix.indexOf('.')) // 0003
                                    : afterPrefix;
                            int seq = Integer.parseInt(seqStr);
                            if (seq > maxSeq.get()) {
                                maxSeq.set(seq);
                            }
                        } catch (NumberFormatException ignored) {
                            // 패턴 불일치 파일 무시
                        }
                    });
        }

        return maxSeq.get() + 1;  // 최대 시퀀스 + 1
    }
}
