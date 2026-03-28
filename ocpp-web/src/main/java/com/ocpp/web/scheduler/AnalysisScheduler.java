package com.ocpp.web.scheduler;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.AnalyzeRequestDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기능1: 정기 배치 분석 스케줄러
 *
 * upload-dir 내 OCPP-LOG-ANALYSIS-* 패턴 파일을 순회하여
 * engine에 fileUrl 방식으로 분석 요청
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final EngineClient engineClient;

    @Value("${ocpp.log.upload-dir}")
    private String uploadDirConfig;

    @Value("${ocpp.web.base-url:http://127.0.0.1:7777}")
    private String webBaseUrl;

    private Path uploadPath;

    private static final String FILE_PREFIX = "OCPP-LOG-ANALYSIS";

    @PostConstruct
    public void init() throws IOException {
        uploadPath = Paths.get(uploadDirConfig)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(uploadPath);
        log.info("[AnalysisScheduler] 업로드 경로 초기화: {}", uploadPath);
    }

    /**
     * 매일 자정 실행
     * 데모 시: @Scheduled(fixedRate = 60000) 으로 교체
     */
    @Scheduled(cron = "0 0 0 * * *")
    // @Scheduled(fixedRate = 60000)
    public void runDailyAnalysis() {
        log.info("[기능1] 정기 배치 분석 시작 - {}", LocalDateTime.now());

        if (!Files.exists(uploadPath)) {
            log.warn("[기능1] 업로드 디렉토리 없음: {}", uploadPath);
            return;
        }

        try (var stream = Files.list(uploadPath)) {
            // OCPP-LOG-ANALYSIS-* 패턴 파일만 대상
            List<Path> logFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(FILE_PREFIX))
                    .sorted()  // 날짜-시퀀스 순 정렬
                    .toList();

            if (logFiles.isEmpty()) {
                log.info("[기능1] 분석할 파일 없음");
                return;
            }

            log.info("[기능1] 대상 파일 {}개 분석 시작", logFiles.size());
            for (Path file : logFiles) {
                analyzeFile(file);
            }
            log.info("[기능1] 배치 완료 - {}개 파일 처리", logFiles.size());

        } catch (IOException e) {
            log.error("[기능1] 배치 분석 중 오류", e);
        }
    }

    private void analyzeFile(Path file) {
        String fileName = file.getFileName().toString();
        try {
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            String fileUrl = webBaseUrl + "/log/upload/" + encodedFileName;

            log.info("[기능1] 분석 요청 - fileName={}, fileUrl={}", fileName, fileUrl);

            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setFileUrl(fileUrl);
            req.setFileName(fileName);

            AnalysisResultDto result = engineClient.analyzeBatch(req);
            log.info("[기능1] 분석 완료 - file={}, 메시지={}, 장애={}",
                    fileName, result.getTotalMsgCount(), result.getFaultCount());

        } catch (Exception e) {
            log.error("[기능1] 분석 실패 - file={}, error={}", fileName, e.getMessage());
        }
    }
}
