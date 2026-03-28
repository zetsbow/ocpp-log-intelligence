package com.ocpp.web.scheduler;

import com.ocpp.web.client.EngineClient;
import com.ocpp.web.dto.AnalysisResultDto;
import com.ocpp.web.dto.AnalyzeRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기능1: 정기 배치 분석
 * upload-dir 내 파일을 순회하여 engine에 filePath 기반으로 분석 요청
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final EngineClient engineClient;

    @Value("${ocpp.log.upload-dir}")
    private String uploadDir;

    /**
     * 매일 자정 실행
     * 데모 시: @Scheduled(fixedRate = 60000) 으로 교체
     */
    @Scheduled(cron = "0 0 0 * * *")
    // @Scheduled(fixedRate = 60000)
    public void runDailyAnalysis() {
        log.info("[기능1] 정기 배치 분석 시작 - {}", LocalDateTime.now());
        Path dir = Paths.get(uploadDir);

        if (!Files.exists(dir)) {
            log.warn("[기능1] 업로드 디렉토리 없음: {}", uploadDir);
            return;
        }

        try (var stream = Files.list(dir)) {
            List<Path> logFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".log") || name.endsWith(".txt");
                    })
                    .toList();

            if (logFiles.isEmpty()) {
                log.info("[기능1] 분석할 파일 없음");
                return;
            }

            for (Path file : logFiles) {
                analyzeFile(file);
            }
            log.info("[기능1] 배치 완료 - {}개 파일 처리", logFiles.size());

        } catch (IOException e) {
            log.error("[기능1] 배치 분석 중 오류", e);
        }
    }

    private void analyzeFile(Path file) {
        try {
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setFilePath(file.toAbsolutePath().toString());   // 경로만 전달
            req.setFileName(file.getFileName().toString());

            AnalysisResultDto result = engineClient.analyzeBatch(req);
            log.info("[기능1] 완료 - file={}, 메시지={}, 장애={}",
                    file.getFileName(), result.getTotalMsgCount(), result.getFaultCount());

        } catch (Exception e) {
            log.error("[기능1] 분석 실패: {}", file.getFileName(), e);
        }
    }
}
