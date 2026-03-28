package com.ocpp.engine.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocpp.engine.dto.OcppMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCPP 로그 파서
 * 지원 포맷:
 *   1) KEVIT 데몬 형식: 2026-03-26 00:00:00 - [TXT][CHARGER_ID] : MESSAGE : [2,"id","Action",{...}]
 *   2) 일반 형식: 2024-01-01 12:00:00 CHARGER_01 [2,"id","Action",{...}]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcppLogParser {

    private final ObjectMapper objectMapper;

    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2})");
    /** KEVIT 데몬 형식: [TXT][CHARGER_ID] */
    private static final Pattern TXT_CHARGER_ID_PATTERN =
            Pattern.compile("\\[TXT\\]\\[([^\\]]+)\\]");
    /** 일반 형식: timestamp CHARGER_ID [...] */
    private static final Pattern CHARGER_ID_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}\\s+(\\S+)\\s+\\[");

    /**
     * 로그 파일 전체 텍스트를 파싱하여 OcppMessage 리스트 반환
     */
    public List<OcppMessage> parse(String logContent) {
        List<OcppMessage> messages = new ArrayList<>();
        if (logContent == null || logContent.isBlank()) return messages;

        String[] lines = logContent.split("\\r?\\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            try {
                OcppMessage msg = parseLine(line.trim());
                if (msg != null) messages.add(msg);
            } catch (Exception e) {
                log.debug("파싱 실패 라인 무시: {}", line);
            }
        }
        log.info("파싱 완료 - 전체 {}줄 중 {}개 메시지 추출", lines.length, messages.size());
        return messages;
    }

    /**
     * 단일 로그 라인 파싱
     */
    public OcppMessage parseLine(String line) {
        String jsonPart = extractJsonPart(line);
        if (jsonPart == null) return null;

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonPart);
        } catch (Exception e) {
            return null;
        }
        if (!root.isArray() || root.size() < 3) return null;

        OcppMessage msg = new OcppMessage();
        msg.setRawLine(line);
        msg.setMessageTypeId(root.get(0).asInt());
        msg.setUniqueId(root.get(1).asText());
        msg.setTimestamp(extractTimestamp(line));
        msg.setChargerId(extractChargerId(line));

        if (msg.isCall()) {
            if (root.size() >= 3) msg.setAction(root.get(2).asText());
            if (root.size() >= 4) {
                JsonNode payload = root.get(3);
                msg.setRawPayload(payload.toString());
                if (payload.has("transactionId"))
                    msg.setTransactionId(payload.get("transactionId").asText());
                if ("DataTransfer".equals(msg.getAction()) && payload.has("messageId"))
                    msg.setDataTransferMessageId(payload.get("messageId").asText());
                msg.setPayloadDetail(extractPayloadDetail(msg.getAction(), payload));
            }
        } else if (msg.isCallResult()) {
            if (root.size() >= 3) {
                JsonNode payload = root.get(2);
                msg.setRawPayload(payload.toString());
                if (payload.has("status"))
                    msg.setStatus(payload.get("status").asText());
                else if (payload.has("idTagInfo") && payload.get("idTagInfo").has("status"))
                    msg.setStatus(payload.get("idTagInfo").get("status").asText());
                else if (payload.has("currentTime"))
                    msg.setStatus(payload.get("currentTime").asText());
                if (payload.has("transactionId"))
                    msg.setTransactionId(payload.get("transactionId").asText());
            }
        } else if (msg.isCallError()) {
            if (root.size() >= 3) msg.setStatus(root.get(2).asText());
        }

        return msg;
    }

    private String extractJsonPart(String line) {
        // KEVIT 데몬 형식: "MESSAGE : [...]"
        int msgIdx = line.indexOf("MESSAGE : ");
        int fromIdx = (msgIdx != -1) ? msgIdx + "MESSAGE : ".length() : 0;
        return extractJsonArray(line, fromIdx);
    }

    private String extractJsonArray(String line, int fromIdx) {
        int idx = line.indexOf('[', fromIdx);
        if (idx == -1) return null;
        int depth = 0, end = -1;
        for (int i = idx; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                if (--depth == 0) { end = i; break; }
            }
        }
        if (end == -1) return null;
        return line.substring(idx, end + 1);
    }

    private LocalDateTime extractTimestamp(String line) {
        Matcher m = TIMESTAMP_PATTERN.matcher(line);
        if (!m.find()) return LocalDateTime.now();
        try {
            String ts = m.group(1).replace('T', ' ');
            return LocalDateTime.parse(ts, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String extractChargerId(String line) {
        // KEVIT 데몬 형식 우선: [TXT][CHARGER_ID]
        Matcher m = TXT_CHARGER_ID_PATTERN.matcher(line);
        if (m.find()) return m.group(1);
        // 일반 형식
        m = CHARGER_ID_PATTERN.matcher(line);
        if (m.find()) return m.group(1);
        return "UNKNOWN";
    }

    private Map<String, String> extractPayloadDetail(String action, JsonNode payload) {
        Map<String, String> detail = new LinkedHashMap<>();
        if (payload == null || payload.isMissingNode()) return detail;
        switch (action != null ? action : "") {
            case "StatusNotification":
                putIfExists(detail, payload, "status");
                putIfExists(detail, payload, "errorCode");
                String sVal = payload.has("status") ? payload.get("status").asText("") : "";
                if ("Faulted".equals(sVal)) {
                    detail.put("vendorErrorCode", payload.has("vendorErrorCode") ? payload.get("vendorErrorCode").asText() : "");
                } else {
                    putIfExists(detail, payload, "vendorErrorCode");
                }
                break;
            case "MeterValues":
                JsonNode mvArr = payload.path("meterValue");
                if (mvArr.isArray() && mvArr.size() > 0) {
                    JsonNode svArr = mvArr.get(0).path("sampledValue");
                    if (svArr.isArray()) {
                        for (JsonNode sv : svArr) {
                            String msr = sv.path("measurand").asText("");
                            String val = sv.path("value").asText("");
                            if ("Energy.Active.Import.Register".equals(msr))  detail.put("Wh", val);
                            else if ("Current.Import".equals(msr))            detail.put("A", val);
                            else if ("Voltage".equals(msr))                   detail.put("V", val);
                            else if ("SoC".equals(msr))                       detail.put("SoC%", val);
                        }
                    }
                }
                break;
            case "StartTransaction":
                putIfExists(detail, payload, "meterStart");
                putIfExists(detail, payload, "idTag");
                break;
            case "StopTransaction":
                putIfExists(detail, payload, "meterStop");
                putIfExists(detail, payload, "reason");
                putIfExists(detail, payload, "idTag");
                break;
            default:
                break;
        }
        return detail;
    }

    private void putIfExists(Map<String, String> map, JsonNode node, String key) {
        if (node.has(key)) map.put(key, node.get(key).asText());
    }
}
