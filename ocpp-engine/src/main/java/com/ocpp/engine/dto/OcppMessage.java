package com.ocpp.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

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

    /** DataTransfer의 messageId (ex: Tariff, AdvancePayment, BoardReceiveData) */
    private String dataTransferMessageId;

    /** 전문별 주요 필드 추출 결과 (파서에서 채움) */
    private Map<String, String> payloadDetail;

    public boolean isCall() {
        return messageTypeId == 2;
    }

    public boolean isCallResult() {
        return messageTypeId == 3;
    }

    public boolean isCallError() {
        return messageTypeId == 4;
    }

    /** 화면 표시용 전문명 (DataTransfer는 messageId 포함) */
    public String getDisplayAction() {
        if ("DataTransfer".equals(action)
                && dataTransferMessageId != null && !dataTransferMessageId.isBlank()) {
            return "DataTransfer(" + dataTransferMessageId + ")";
        }
        return action;
    }
}
