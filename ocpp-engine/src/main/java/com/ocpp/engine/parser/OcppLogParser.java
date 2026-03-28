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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCPP 로그 파서
 * 지원 포맷:
 *   1) 순수 OCPP JSON: [2,"id","Action",{...}]
 *   2) 타임스탬프 포함: 2024-01-01 12:00:00 CHARGER_01 [2,"id","Action",{...}]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcppLogParser {

    private final ObjectMapper objectMapper;

    // 로그 라인에서 OCPP JSON 배열 추출 패턴
    private static final Pattern OCPP_ARRAY_PATTERN = Pattern.compile("(\\[\\s*[234]\\s*,.*?\\]\\s*)$");
    // 타임스탬프 패턴
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2})");
    // 충전기 ID 패턴 (타임스탬프 뒤 첫 번째 토큰)
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
        // OCPP 배열 부분 추출
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

        // 타임스탬프 추출
        msg.setTimestamp(extractTimestamp(line));
        // 충전기 ID 추출
        msg.setChargerId(extractChargerId(line));

        if (msg.isCall()) {
            // [2, uniqueId, Action, Payload]
            if (root.size() >= 3) msg.setAction(root.get(2).asText());
            if (root.size() >= 4) {
                JsonNode payload = root.get(3);
                msg.setRawPayload(payload.toString());
                // transactionId 추출 시도
                if (payload.has("transactionId"))
                    msg.setTransactionId(payload.get("transactionId").asText());
            }
        } else if (msg.isCallResult()) {
            // [3, uniqueId, Payload]
            if (root.size() >= 3) {
                JsonNode payload = root.get(2);
                msg.setRawPayload(payload.toString());
                // status 추출
                if (payload.has("status"))
                    msg.setStatus(payload.get("status").asText());
                else if (payload.has("idTagInfo") && payload.get("idTagInfo").has("status"))
                    msg.setStatus(payload.get("idTagInfo").get("status").asText());
                if (payload.has("transactionId"))
                    msg.setTransactionId(payload.get("transactionId").asText());
            }
        } else if (msg.isCallError()) {
            // [4, uniqueId, ErrorCode, ErrorDescription, ErrorDetails]
            if (root.size() >= 3) msg.setStatus(root.get(2).asText());
        }

        return msg;
    }

    private String extractJsonPart(String line) {
        // 대괄호로 시작하는 OCPP 배열 찾기
        int idx = line.indexOf('[');
        if (idx == -1) return null;
        // 마지막 ']' 찾기 (중첩 고려)
        int depth = 0;
        int end = -1;
        for (int i = idx; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) { end = i; break; }
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
        Matcher m = CHARGER_ID_PATTERN.matcher(line);
        if (m.find()) return m.group(1);
        return "UNKNOWN";
    }
}
