package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnalyzeRequestDto {
    /** 충전기 ID 필터 (null이면 전체) */
    private String chargerId;

    /** 시간 범위 필터 */
    private LocalDateTime fromTime;
    private LocalDateTime toTime;

    /** 서버에 저장된 로그 파일 절대 경로 (engine이 직접 읽음) */
    private String filePath;

    /** 화면 표시용 원본 파일명 */
    private String fileName;

    /** 하위 호환: 파일 내용 직접 전달 (배치용) */
    private String logContent;
}
