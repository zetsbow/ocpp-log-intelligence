package com.ocpp.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 업로드된 로그 파일을 URL로 제공하는 컨트롤러
 *
 * GET /log/upload/{fileName}   → 파일 내용 다운로드 (브라우저 or engine 호출)
 * GET /log/upload              → 업로드된 파일 목록 조회 (JSON)
 *
 * 예) http://localhost:7777/log/upload/20250328_100000_sample.log
 */
@Slf4j
@RestController
@RequestMapping("/log/upload")
public class FileServeController {

    @Value("${ocpp.log.upload-dir}")
    private String uploadDir;

    /**
     * 파일 내용 다운로드
     * GET /log/upload/{fileName}
     *
     * 브라우저에서 직접 열거나 engine이 RestTemplate으로 호출 가능
     * Content-Type: text/plain; charset=UTF-8
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) throws IOException {

        // 경로 탐색 공격 방지 (../ 같은 상위 경로 이동 차단)
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            log.warn("[FileServe] 잘못된 파일명 요청 차단: {}", fileName);
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();

        // upload-dir 하위 경로인지 검증
        if (!filePath.startsWith(Paths.get(uploadDir).normalize())) {
            log.warn("[FileServe] 허용 경로 외 접근 차단: {}", filePath);
            return ResponseEntity.status(403).build();
        }

        if (!Files.exists(filePath)) {
            log.warn("[FileServe] 파일 없음: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        log.info("[FileServe] 파일 제공: {} ({} bytes)", fileName, Files.size(filePath));

        InputStream is = Files.newInputStream(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName + "\"")  // inline: 브라우저에서 바로 표시
                .contentType(new MediaType("text", "plain",
                        java.nio.charset.StandardCharsets.UTF_8))
                .contentLength(Files.size(filePath))
                .body(new InputStreamResource(is));
    }

    /**
     * 업로드된 파일 목록 조회
     * GET /log/upload
     *
     * 응답 예시:
     * [
     *   { "fileName": "20250328_100000_sample.log", "size": 12345, "url": "/log/upload/20250328_100000_sample.log" },
     *   ...
     * ]
     */
    @GetMapping
    public ResponseEntity<List<FileInfo>> listFiles() throws IOException {
        Path dir = Paths.get(uploadDir);
        if (!Files.exists(dir)) {
            return ResponseEntity.ok(List.of());
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<FileInfo> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".log") || name.endsWith(".txt");
                    })
                    .sorted((a, b) -> {
                        try {
                            // 최신 파일 먼저
                            return Files.getLastModifiedTime(b)
                                    .compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .map(p -> {
                        try {
                            return new FileInfo(
                                    p.getFileName().toString(),
                                    Files.size(p),
                                    "/log/upload/" + p.getFileName().toString(),
                                    Files.getLastModifiedTime(p).toString()
                            );
                        } catch (IOException e) {
                            return new FileInfo(p.getFileName().toString(), 0L, "", "");
                        }
                    })
                    .collect(Collectors.toList());

            log.info("[FileServe] 파일 목록 조회: {}개", files.size());
            return ResponseEntity.ok(files);
        }
    }

    /**
     * 파일 정보 응답 DTO
     */
    public record FileInfo(
            String fileName,
            long   size,
            String url,
            String lastModified
    ) {}
}
