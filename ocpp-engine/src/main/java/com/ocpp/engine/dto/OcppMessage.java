package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCPP 전문 메시지 파싱 결과 DTO
 * OCPP 메시지 형식: [MessageTypeId, UniqueId, Action, Payload]
 *   - MessageTypeId: 2=Call, 3=CallResult, 4=CallError
 */
@Data
public class OcppMessage {

    /** 메시지 타입: 2=Call, 3=CallResult, 4=CallError */
    private int messageTypeId;

    /** 고유 메시지 ID */
    private String uniqueId;

    /** 액션명 (Call일 때): BootNotification, StartTransaction 등 */
    private String action;

    /** 응답 상태 (CallResult일 때): Accepted, Invalid, Rejected 등 */
    private String status;

    /** 트랜잭션 ID */
    private String transactionId;

    /** 충전기 ID */
    private String chargerId;

    /** 원본 JSON 페이로드 */
    private String rawPayload;

    /** 로그 타임스탬프 */
    private LocalDateTime timestamp;

    /** 원본 로그 라인 */
    private String rawLine;

    public boolean isCall() {
        return messageTypeId == 2;
    }

    public boolean isCallResult() {
        return messageTypeId == 3;
    }

    public boolean isCallError() {
        return messageTypeId == 4;
    }
}
