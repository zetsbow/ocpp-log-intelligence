package com.ocpp.engine.dto;

import lombok.Data;

import java.util.Map;

@Data
public class OcppFlowEntry {
    private Long id;
    private String sessionId;

    /** OCPP 메시지 ID ([2,"<id>","Action",{...}]의 두 번째 요소) */
    private String messageId;

    /** 로그 타임스탬프 (yyyy-MM-dd HH:mm:ss) */
    private String timestamp;

    /** 충전기 ID - 모든 행에 포함 */
    private String chargerId;

    /** 트랜잭션 ID (StartTransaction ~ StopTransaction 세션 식별) */
    private String transactionId;

    /** 전문 이름 (ex: Heartbeat, DataTransfer(Tariff), StatusNotification) */
    private String action;

    /** 메시지 유형: Call / CallResult / CallError */
    private String messageType;

    /** 통신 방향: CP→CS / CS→CP */
    private String direction;

    /** 응답 상태 (CallResult): Accepted, Rejected, currentTime=... 등 */
    private String status;

    /** 위반이 발생한 트랜잭션의 전문 여부: Y / N */
    private String isFault;

    /**
     * DB 저장용 상세 정보 문자열
     * - StatusNotification : "status=Charging, errorCode=NoError"
     *                        "status=Faulted, errorCode=OtherError"
     * - MeterValues        : "Wh=3770, A=119, V=714, SoC%=21"
     * - StartTransaction   : "meterStart=12072910, idTag=1010010197328263"
     * - StartTx CallResult : "transactionId=326000607"
     * - StopTransaction    : "meterStop=12123110, reason=Local, idTag=..."
     */
    private String detailText;

    /** 내부 처리용 (DB 미저장) */
    private transient Map<String, String> detail;
}
