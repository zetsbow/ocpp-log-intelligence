package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnalyzeRequest {
    /** 충전기 ID 필터 */
    private String chargerId;

    /** 시간 범위 필터 */
    private LocalDateTime fromTime;
    private LocalDateTime toTime;

    /**
     * 파일 URL (URL 인코딩 적용)
     * 예) http://localhost:7777/log/upload/%EC%A0%95%EC%83%81...txt
     * engine이 HTTP GET으로 호출하여 파일 내용을 읽음
     */
    private String fileUrl;

    /** 원본 파일명 (이력 저장용) */
    private String fileName;

    /** 배치 스케줄러에서 파일 내용을 직접 전달할 때 사용 */
    private String logContent;
}
