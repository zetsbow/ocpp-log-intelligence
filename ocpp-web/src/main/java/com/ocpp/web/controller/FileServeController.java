package com.ocpp.web.controller;

/**
 * 동일 서버 배포 구조에서는 사용하지 않습니다.
 *
 * 향후 web / engine을 별도 서버로 분리할 경우:
 *   - file-transfer-mode: url 설정
 *   - 이 클래스에 GET /files/{fileName} 엔드포인트 구현
 *   - engine이 해당 URL 호출로 파일 내용 다운로드
 *
 * 현재는 동일 서버에서 C:/ocpp-logs/upload 경로를 공유하므로
 * engine이 Files.readString(filePath)로 직접 읽습니다.
 */
public class FileServeController {
    // 현재 미사용 — 서버 분리 배포 시 활성화
}
