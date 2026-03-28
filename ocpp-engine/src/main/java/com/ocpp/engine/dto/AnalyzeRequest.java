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

    /** 서버 저장 경로 — engine이 직접 파일을 읽음 */
    private String filePath;

    /** 원본 파일명 (이력 저장용) */
    private String fileName;

    /** 하위 호환: 파일 내용 직접 전달 (배치 스케줄러용) */
    private String logContent;
}
