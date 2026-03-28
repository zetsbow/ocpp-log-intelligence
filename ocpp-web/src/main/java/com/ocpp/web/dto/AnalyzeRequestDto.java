package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnalyzeRequestDto {
    /** 충전기 ID 필터 (null = 전체 분석) */
    private String chargerId;

    /** 시간 범위 필터 (null = 전체 시간) */
    private LocalDateTime fromTime;
    private LocalDateTime toTime;

    /**
     * 저장된 파일의 절대 경로
     * web과 engine이 동일 서버이므로 경로 공유 가능
     * 예) C:/ocpp-logs/upload/20250328_100000_sample.log
     */
    private String filePath;

    /** 화면 표시용 원본 파일명 */
    private String fileName;

    /** 배치 스케줄러에서 파일 내용을 직접 전달할 때 사용 */
    private String logContent;
}
