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

    private static final String FILE_PREFIX = "OCPP-LOG-ANALYSIS-";

    @PostConstruct
    public void init() throws IOException {
        uploadPath = Paths.get(uploadDirConfig)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(uploadPath);
        log.info("[AnalysisScheduler] 업로드 경로 초기화: {}", uploadPath);
    }

    @Scheduled(cron = "0 0 0 * * *")
    // @Scheduled(fixedRate = 60000)
    public void runDailyAnalysis() {
        log.info("[기능1] 정기 배치 분석 시작 - {}", LocalDateTime.now());

        if (!Files.exists(uploadPath)) {
            log.warn("[기능1] 업로드 디렉토리 없음: {}", uploadPath);
            return;
        }

        try (var stream = Files.list(uploadPath)) {
            List<Path> logFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(FILE_PREFIX))
                    .sorted()
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
            // sessionId 추출: OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001
            String sessionId = extractSessionId(fileName);

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            String fileUrl = webBaseUrl + "/log/upload/" + encodedFileName;

            log.info("[기능1] 분석 요청 - sessionId={}, fileName={}", sessionId, fileName);

            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setSessionId(sessionId);
            req.setFileUrl(fileUrl);
            req.setFileName(fileName);

            AnalysisResultDto result = engineClient.analyzeBatch(req);
            log.info("[기능1] 분석 완료 - sessionId={}, 트랜잭션={}, 장애={}",
                    result.getSessionId(),
                    result.getTotalTransaction(),
                    result.getFaultTransactionCount());

        } catch (Exception e) {
            log.error("[기능1] 분석 실패 - file={}, error={}", fileName, e.getMessage());
        }
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
            return nameWithoutExt.substring(FILE_PREFIX.length());
        }
        return nameWithoutExt;
    }
}
