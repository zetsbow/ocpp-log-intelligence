package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 기능2: 충전기 로그 분석 요청 DTO (ocpp-web → ocpp-engine)
 */
@Data
public class AnalyzeRequest {
    /** 분석 대상 충전기 ID */
    private String chargerId;

    /** 조회 시작 시간 */
    private LocalDateTime fromTime;

    /** 조회 종료 시간 */
    private LocalDateTime toTime;

    /** 업로드된 로그 파일 내용 (raw text) */
    private String logContent;

    /** 파일명 */
    private String fileName;

    /** 서버 로컬 파일 경로 (logContent가 없을 때 파일 직접 읽기) */
    private String filePath;
}
