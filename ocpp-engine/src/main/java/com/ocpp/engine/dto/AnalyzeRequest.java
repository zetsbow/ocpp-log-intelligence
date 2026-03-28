package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 로그 분석 요청 DTO (ocpp-web → ocpp-engine)
 *
 * [sessionId 생성 규칙]
 * - ocpp-web에서 파일명의 뒷부분을 추출하여 전달
 * - 파일명: OCPP-LOG-ANALYSIS-20260328-0001.txt
 * - sessionId: 20260328-0001 (CHAR 13자리)
 * - analysis_result 테이블의 session_id(PK) 로 직접 사용
 */
@Data
public class AnalyzeRequest {

    /**
     * 분석 세션 ID — ocpp-web에서 파일명으로부터 추출하여 전달
     * 예) OCPP-LOG-ANALYSIS-20260328-0001.txt → 20260328-0001
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
     * engine이 HTTP GET으로 호출하여 파일 내용을 읽음
     */
    private String fileUrl;

    /** 원본 파일명 (이력 저장용) */
    private String fileName;

    /** 배치 스케줄러에서 파일 내용을 직접 전달할 때 사용 */
    private String logContent;
}
