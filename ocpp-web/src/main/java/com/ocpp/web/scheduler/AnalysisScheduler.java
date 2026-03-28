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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * 기능1: 정기 OCPP 풀로그 자동 분석 스케줄러
 * - 기본: 매일 자정 실행 (cron)
 * - 데모용: application.yml에서 fixedRate로 변경 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final EngineClient engineClient;

    /** 로그 파일이 저장된 디렉토리 (application.yml에서 설정) */
    @Value("${ocpp.log.dir:./logs}")
    private String logDir;

    /**
     * 매일 자정 전체 로그 디렉토리 배치 분석
     * 데모 시: fixedRate = 60000 (1분마다)으로 변경
     */
    @Scheduled(cron = "0 0 0 * * *")
    // @Scheduled(fixedRate = 60000) // ← 데모용 주석 해제
    public void runDailyAnalysis() {
        log.info("[기능1] 정기 배치 분석 시작 - {}", LocalDateTime.now());
        Path dir = Paths.get(logDir);
        if (!Files.exists(dir)) {
            log.warn("[기능1] 로그 디렉토리 없음: {}", logDir);
            return;
        }

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> logFiles = files
                    .filter(p -> p.toString().endsWith(".log") || p.toString().endsWith(".txt"))
                    .toList();

            if (logFiles.isEmpty()) {
                log.info("[기능1] 분석할 로그 파일 없음");
                return;
            }

            for (Path file : logFiles) {
                analyzeFile(file);
            }
            log.info("[기능1] 배치 분석 완료 - {}개 파일 처리", logFiles.size());

        } catch (IOException e) {
            log.error("[기능1] 배치 분석 중 오류", e);
        }
    }

    private void analyzeFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            AnalyzeRequestDto req = new AnalyzeRequestDto();
            req.setLogContent(content);
            req.setFileName(file.getFileName().toString());

            AnalysisResultDto result = engineClient.analyzeBatch(req);
            log.info("[기능1] 파일 분석 완료 - file={}, 총메시지={}, 장애={}",
                    file.getFileName(), result.getTotalMsgCount(), result.getFaultCount());

        } catch (IOException e) {
            log.error("[기능1] 파일 읽기 실패: {}", file, e);
        } catch (Exception e) {
            log.error("[기능1] 엔진 호출 실패: {}", file, e);
        }
    }
}
