package com.ocpp.web.controller;

import jakarta.annotation.PostConstruct;
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
 * 업로드된 로그 파일을 URL로 제공
 *
 * GET /log/upload/{fileName}  → 파일 내용 반환
 * GET /log/upload             → 파일 목록 반환 (JSON)
 */
@Slf4j
@RestController
@RequestMapping("/log/upload")
public class FileServeController {

    @Value("${ocpp.log.upload-dir}")
    private String uploadDirConfig;

    /** 정규화된 절대 경로 */
    private Path uploadPath;

    @PostConstruct
    public void init() throws IOException {
        uploadPath = Paths.get(uploadDirConfig)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(uploadPath);
        log.info("[FileServe] 업로드 경로 초기화: {}", uploadPath);
    }

    /**
     * GET /log/upload/{fileName}
     * 파일 내용 반환 (브라우저에서 바로 열기 가능)
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) throws IOException {

        // 경로 탐색 공격 방지
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            log.warn("[FileServe] 잘못된 파일명 차단: {}", fileName);
            return ResponseEntity.badRequest().build();
        }

        Path filePath = uploadPath.resolve(fileName).normalize();

        // uploadPath 하위인지 검증
        if (!filePath.startsWith(uploadPath)) {
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .contentLength(Files.size(filePath))
                .body(new InputStreamResource(is));
    }

    /**
     * GET /log/upload
     * 업로드된 파일 목록 반환 (최신순)
     */
    @GetMapping
    public ResponseEntity<List<FileInfo>> listFiles() throws IOException {
        if (!Files.exists(uploadPath)) {
            return ResponseEntity.ok(List.of());
        }

        try (Stream<Path> stream = Files.list(uploadPath)) {
            List<FileInfo> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".log") || n.endsWith(".txt");
                    })
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) { return 0; }
                    })
                    .map(p -> {
                        try {
                            return new FileInfo(
                                    p.getFileName().toString(),
                                    Files.size(p),
                                    "/log/upload/" + p.getFileName(),
                                    Files.getLastModifiedTime(p).toString()
                            );
                        } catch (IOException e) {
                            return new FileInfo(p.getFileName().toString(), 0L, "", "");
                        }
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(files);
        }
    }

    public record FileInfo(String fileName, long size, String url, String lastModified) {}
}
