package com.ocpp.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnalyzeRequestDto {

    /**
     * 분석 세션 ID — 파일명의 뒷부분에서 추출
     * 파일명: OCPP-LOG-ANALYSIS-20260328-0001.txt
     * sessionId: 20260328-0001 (CHAR 13자리)
     * analysis_result.session_id(PK) 로 직접 사용
     */
    private String sessionId;

    /** 충전기 ID 필터 (null = 전체) */
    private String chargerId;

    /** 시간 범위 필터 */
    private LocalDateTime fromTime;
    private LocalDateTime toTime;

    /**
     * 파일 URL (URL 인코딩 적용)
     * 예) http://127.0.0.1:7777/log/upload/OCPP-LOG-ANALYSIS-20260328-0001.txt
     */
    private String fileUrl;

    /** 화면 표시용 원본 파일명 */
    private String fileName;

    /** 배치 스케줄러에서 파일 내용을 직접 전달할 때 사용 */
    private String logContent;
}
