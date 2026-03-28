package com.ocpp.engine.service;

import com.ocpp.engine.dto.*;
import com.ocpp.engine.mapper.AnalysisResultMapper;
import com.ocpp.engine.mapper.FaultDetectionMapper;
import com.ocpp.engine.mapper.FlowViolationMapper;
import com.ocpp.engine.mapper.OcppFlowEntryMapper;
import com.ocpp.engine.parser.OcppLogParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final OcppLogParser parser;
    private final PatternMatcherService patternMatcher;
    private final AnalysisResultMapper analysisResultMapper;
    private final FaultDetectionMapper faultDetectionMapper;
    private final OcppFlowEntryMapper ocppFlowEntryMapper;
    private final FlowViolationMapper flowViolationMapper;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 기능1·2: 로그 분석 메인 로직
     */
    @Transactional
    public AnalysisResult analyze(AnalyzeRequest request) {
        log.info("분석 시작 - 충전기: {}, 파일: {}", request.getChargerId(), request.getFileName());

        // 0. filePath로 파일 직접 읽기 (logContent가 없을 때)
        if ((request.getLogContent() == null || request.getLogContent().isBlank())
                && request.getFileUrl() != null && !request.getFileUrl().isBlank()) {
            try {
                request.setLogContent(Files.readString(Paths.get(request.getFileUrl())));
                if (request.getFileName() == null)
                    request.setFileName(Paths.get(request.getFileUrl()).getFileName().toString());
            } catch (IOException e) {
                throw new RuntimeException("로그 파일 읽기 실패: " + request.getFileUrl(), e);
            }
        }

        // 1. 파싱
        List<OcppMessage> allMessages = parser.parse(request.getLogContent());

        // 2. 충전기 ID + 시간 필터링
        List<OcppMessage> filtered = filterMessages(allMessages, request);

        // 3. 패턴 매칭 (기존 FaultPattern 기반)
        List<FaultDetection> detections = patternMatcher.match(filtered);

        // 4. 세션 ID 추출 (파일명 마지막 _ 뒤 문자열)
        String sessionId = extractSessionId(request.getFileName());

        // 5. total_transaction: 시작 신호(Preparing/Authorize/RemoteStartTransaction)가 있는 완전한 세션 건수
        int transactionCount = countCompleteSessionStarts(filtered);

        // 6. charger_id: 파라미터 있으면 해당 값, 없으면 null (전체 분석)
        final String resolvedChargerId = (request.getChargerId() != null && !request.getChargerId().isBlank())
                ? request.getChargerId() : null;

        // 7. 흐름 검증 (위반 목록 - error_transaction_count 계산에 사용)
        List<FlowViolation> violations = validateFlow(filtered, resolvedChargerId);

        // 8. error_transaction_count: 위반이 1건 이상인 transaction_id 건수
        int errorTransactionCount = (int) violations.stream()
                .map(FlowViolation::getTransactionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // 9. 분석 이력 저장
        AnalysisResult result = new AnalysisResult();
        result.setChargerId(resolvedChargerId);
        result.setAnalyzedAt(LocalDateTime.now());
        result.setTotalTransaction(transactionCount);
        result.setFaultTransactionCount(errorTransactionCount);
        result.setAnalysisType(request.getChargerId() != null ? "MANUAL" : "BATCH");
        result.setFileName(request.getFileName());
        result.setSummary(buildSummary(filtered, detections));
        result.setSessionId(sessionId);
        analysisResultMapper.insert(result);

        // 10. transaction_detail INSERT (max_allowed_packet 초과 방지를 위해 100건씩 분할)
        Set<String> faultTxIds = violations.stream()
                .map(FlowViolation::getTransactionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<OcppFlowEntry> flowEntries = buildFlowEntries(filtered, resolvedChargerId);
        flowEntries.forEach(e -> {
            e.setSessionId(sessionId);
            e.setIsFault(e.getTransactionId() != null && faultTxIds.contains(e.getTransactionId()) ? "Y" : "N");
        });
        for (int i = 0; i < flowEntries.size(); i += 100) {
            ocppFlowEntryMapper.insertAll(flowEntries.subList(i, Math.min(i + 100, flowEntries.size())));
        }

        // 11. flow_violation INSERT
        violations.forEach(v -> v.setSessionId(sessionId));
        if (!violations.isEmpty()) flowViolationMapper.insertAll(violations);

        result.setDetections(detections);
        log.info("분석 완료 - 총 {}개 메시지, {}개 장애 탐지, 충전 {}건 (위반 {}건)",
                filtered.size(), detections.size(), transactionCount, errorTransactionCount);
        return result;
    }

    /**
     * 파일명에서 세션 ID 추출
     * ex) OCPP16_Log-2026-03-26.0_20260328-0001.txt → "20260328-0001"
     * 마지막 _ 뒤, 확장자 앞 문자열. 없으면 null.
     */
    private String extractSessionId(String fileName) {
        if (fileName == null) return null;
        String name = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        int idx = name.lastIndexOf('_');
        return idx != -1 ? name.substring(idx + 1) : null;
    }

    private List<OcppMessage> filterMessages(List<OcppMessage> messages, AnalyzeRequest req) {
        return messages.stream()
                .filter(m -> {
                    if (req.getChargerId() == null || req.getChargerId().isBlank()) return true;
                    return req.getChargerId().equalsIgnoreCase(m.getChargerId());
                })
                .filter(m -> {
                    if (req.getFromTime() == null || m.getTimestamp() == null) return true;
                    return !m.getTimestamp().isBefore(req.getFromTime());
                })
                .filter(m -> {
                    if (req.getToTime() == null || m.getTimestamp() == null) return true;
                    return !m.getTimestamp().isAfter(req.getToTime());
                })
                .collect(Collectors.toList());
    }

    private String buildSummary(List<OcppMessage> messages, List<FaultDetection> detections) {
        long callCount = messages.stream().filter(OcppMessage::isCall).count();
        long resultCount = messages.stream().filter(OcppMessage::isCallResult).count();
        long errorCount = messages.stream().filter(OcppMessage::isCallError).count();
        long highCount = detections.stream().filter(d -> "HIGH".equals(d.getSeverity())).count();
        long medCount = detections.stream().filter(d -> "MEDIUM".equals(d.getSeverity())).count();
        return String.format("Call: %d / CallResult: %d / CallError: %d | 장애 HIGH: %d, MEDIUM: %d",
                callCount, resultCount, errorCount, highCount, medCount);
    }

    // ── 흐름 요약 ────────────────────────────────────────────────────────────

    private List<OcppFlowEntry> buildFlowEntries(List<OcppMessage> messages, String fallbackChargerId) {
        // Call uniqueId → displayAction 매핑
        Map<String, String> uidToDisplay = messages.stream()
                .filter(OcppMessage::isCall)
                .filter(m -> m.getUniqueId() != null)
                .collect(Collectors.toMap(
                        OcppMessage::getUniqueId,
                        OcppMessage::getDisplayAction,
                        (a, b) -> a));

        // Pre-scan: StartTransaction CallResult uid → transactionId
        Map<String, String> uidToTxId = new HashMap<>();
        for (OcppMessage msg : messages) {
            if (msg.isCallResult() && msg.getTransactionId() != null
                    && "StartTransaction".equals(uidToDisplay.get(msg.getUniqueId()))) {
                uidToTxId.put(msg.getUniqueId(), msg.getTransactionId());
            }
        }

        List<OcppFlowEntry> entries = new ArrayList<>();
        String activeTxId = null;

        for (OcppMessage msg : messages) {
            OcppFlowEntry e = new OcppFlowEntry();
            e.setMessageId(msg.getUniqueId());
            e.setTimestamp(msg.getTimestamp() != null ? msg.getTimestamp().format(TS_FMT) : "");
            e.setChargerId(msg.getChargerId() != null ? msg.getChargerId() : fallbackChargerId);

            String resolvedAction = msg.isCall()
                    ? msg.getAction()
                    : uidToDisplay.getOrDefault(msg.getUniqueId(), "");

            if (msg.isCall()) {
                e.setAction(msg.getDisplayAction());
                e.setMessageType("Call");
                e.setDirection("CP→CS");
                e.setDetail(msg.getPayloadDetail());
                e.setDetailText(toDetailText(msg.getPayloadDetail()));

            } else if (msg.isCallResult()) {
                e.setAction(uidToDisplay.getOrDefault(msg.getUniqueId(), "Unknown"));
                e.setMessageType("CallResult");
                e.setDirection("CS→CP");
                e.setStatus(msg.getStatus());
                if ("StartTransaction".equals(resolvedAction) && msg.getTransactionId() != null) {
                    Map<String, String> d = new LinkedHashMap<>();
                    d.put("transactionId", msg.getTransactionId());
                    e.setDetail(d);
                    e.setDetailText("transactionId=" + msg.getTransactionId());
                }

            } else {
                e.setAction(uidToDisplay.getOrDefault(msg.getUniqueId(), "Unknown"));
                e.setMessageType("CallError");
                e.setDirection("CS→CP");
                e.setStatus(msg.getStatus());
            }

            // transactionId 결정
            if (msg.isCall() && "StartTransaction".equals(resolvedAction)) {
                e.setTransactionId(uidToTxId.get(msg.getUniqueId()));
            } else if (msg.isCallResult() && "StartTransaction".equals(resolvedAction)) {
                if (msg.getTransactionId() != null) activeTxId = msg.getTransactionId();
                e.setTransactionId(activeTxId);
            } else if ("StopTransaction".equals(resolvedAction)) {
                e.setTransactionId(activeTxId);
                if (msg.isCallResult()) activeTxId = null;
            } else {
                e.setTransactionId(activeTxId);
            }

            entries.add(e);
        }
        return entries;
    }

    private String toDetailText(Map<String, String> detail) {
        if (detail == null || detail.isEmpty()) return null;
        return detail.entrySet().stream()
                .map(en -> en.getKey() + "=" + en.getValue())
                .collect(Collectors.joining(", "));
    }

    // ── 흐름 검증 ────────────────────────────────────────────────────────────

    private List<FlowViolation> validateFlow(List<OcppMessage> messages, String chargerId) {
        List<FlowViolation> violations = new ArrayList<>();
        List<OcppMessage> calls = messages.stream()
                .filter(OcppMessage::isCall)
                .collect(Collectors.toList());

        // uid → displayAction (Call 메시지 기준)
        Map<String, String> uidToDisplay = messages.stream()
                .filter(OcppMessage::isCall)
                .filter(m -> m.getUniqueId() != null)
                .collect(Collectors.toMap(
                        OcppMessage::getUniqueId,
                        OcppMessage::getDisplayAction,
                        (a, b) -> a));

        // uid → transactionId (StartTransaction CallResult 기준)
        Map<String, String> uidToTxId = new HashMap<>();
        for (OcppMessage msg : messages) {
            if (msg.isCallResult() && msg.getTransactionId() != null
                    && "StartTransaction".equals(uidToDisplay.get(msg.getUniqueId()))) {
                uidToTxId.put(msg.getUniqueId(), msg.getTransactionId());
            }
        }

        checkHeartbeatInterval(calls, violations, chargerId, messages, uidToDisplay);
        checkFaultedStatus(calls, violations, chargerId, messages, uidToDisplay, uidToTxId);
        checkChargingSessionFlow(calls, violations, chargerId, uidToTxId, messages);

        return violations;
    }

    /** 특정 시각에 활성화된 transactionId 반환 (없으면 null) */
    private String findActiveTxId(List<OcppMessage> messages, Map<String, String> uidToDisplay,
                                   LocalDateTime ts) {
        String activeTxId = null;
        for (OcppMessage msg : messages) {
            if (msg.getTimestamp() == null || msg.getTimestamp().isAfter(ts)) break;
            if (!msg.isCallResult()) continue;
            String action = uidToDisplay.getOrDefault(msg.getUniqueId(), "");
            if ("StartTransaction".equals(action) && msg.getTransactionId() != null) {
                activeTxId = msg.getTransactionId();
            } else if ("StopTransaction".equals(action)) {
                activeTxId = null;
            }
        }
        return activeTxId;
    }

    /** Heartbeat 간격이 11분을 초과하면 WARN */
    private void checkHeartbeatInterval(List<OcppMessage> calls, List<FlowViolation> violations,
                                         String chargerId, List<OcppMessage> allMessages,
                                         Map<String, String> uidToDisplay) {

        List<OcppMessage> heartbeats = calls.stream()
                .filter(m -> "Heartbeat".equals(m.getAction()) && m.getTimestamp() != null)
                .collect(Collectors.toList());

        for (int i = 1; i < heartbeats.size(); i++) {
            long gapMin = ChronoUnit.MINUTES.between(
                    heartbeats.get(i - 1).getTimestamp(),
                    heartbeats.get(i).getTimestamp());
            if (gapMin > 11) {
                LocalDateTime ts = heartbeats.get(i).getTimestamp();
                addViolation(violations, "WARN", ts,
                        String.format("Heartbeat 간격 초과: %s → %s (%d분)",
                                heartbeats.get(i - 1).getTimestamp().format(TS_FMT),
                                ts.format(TS_FMT),
                                gapMin),
                        chargerId,
                        findActiveTxId(allMessages, uidToDisplay, ts), null);
            }
        }
    }

    /** StatusNotification Faulted 감지 */
    private void checkFaultedStatus(List<OcppMessage> calls, List<FlowViolation> violations,
                                     String chargerId, List<OcppMessage> allMessages,
                                     Map<String, String> uidToDisplay, Map<String, String> uidToTxId) {
        calls.stream()
                .filter(m -> "StatusNotification".equals(m.getAction()))
                .filter(m -> m.getPayloadDetail() != null
                        && "Faulted".equals(m.getPayloadDetail().get("status")))
                .forEach(m -> {
                    String errorCode = m.getPayloadDetail().getOrDefault("errorCode", "");
                    String vendorErrorCode = m.getPayloadDetail().getOrDefault("vendorErrorCode", "");
                    String msg = "StatusNotification Faulted - errorCode=" + errorCode
                            + (vendorErrorCode.isBlank() ? "" : ", vendorErrorCode=" + vendorErrorCode);
                    addViolation(violations, "ERROR", m.getTimestamp(), msg, chargerId,
                            findActiveTxId(allMessages, uidToDisplay, m.getTimestamp()), m.getUniqueId());
                });
    }

    /** 충전 세션(StartTx→StopTx)별 KEVIT 흐름 검증 */
    private void checkChargingSessionFlow(List<OcppMessage> calls, List<FlowViolation> violations,
                                           String chargerId, Map<String, String> uidToTxId,
                                           List<OcppMessage> allMessages) {
        List<OcppMessage> startTxList = calls.stream()
                .filter(m -> "StartTransaction".equals(m.getAction()))
                .collect(Collectors.toList());
        List<OcppMessage> stopTxList = calls.stream()
                .filter(m -> "StopTransaction".equals(m.getAction()))
                .collect(Collectors.toList());

        for (int i = 0; i < startTxList.size(); i++) {
            OcppMessage st = startTxList.get(i);
            if (st.getTimestamp() == null) continue;

            LocalDateTime stTs     = st.getTimestamp();
            LocalDateTime nextStTs = (i + 1 < startTxList.size()) ? startTxList.get(i + 1).getTimestamp() : null;
            String sessionLabel    = String.format("세션%d(%s)", i + 1, stTs.format(TS_FMT));
            String txId            = uidToTxId.get(st.getUniqueId());

            // StartTx 이전 10분 내 전체 메시지 (CS→CP 포함, RemoteStartTransaction 감지 위해 allMessages 사용)
            LocalDateTime windowStart = stTs.minusMinutes(10);
            List<OcppMessage> preAll = allMessages.stream()
                    .filter(m -> m.getTimestamp() != null
                            && !m.getTimestamp().isBefore(windowStart)
                            && m.getTimestamp().isBefore(stTs))
                    .collect(Collectors.toList());

            // 세션 시작 신호 확인: Preparing / Authorize / RemoteStartTransaction 중 하나라도 있으면 완전한 세션
            boolean hasSessionStart = preAll.stream().anyMatch(this::isSessionStartSignal);
            if (!hasSessionStart) {
                // 로그 파일 범위 밖에서 시작된 세션 → 흐름 검증 생략 (오탐 방지)
                continue;
            }

            // 대응 StopTx 탐색
            OcppMessage stp = stopTxList.stream()
                    .filter(m -> m.getTimestamp() != null
                            && m.getTimestamp().isAfter(stTs)
                            && (nextStTs == null || m.getTimestamp().isBefore(nextStTs)))
                    .findFirst().orElse(null);

            if (stp == null) {
                addViolation(violations, "WARN", stTs,
                        sessionLabel + ": 대응하는 StopTransaction 없음",
                        chargerId, txId, null);
                continue;
            }

            LocalDateTime stpTs = stp.getTimestamp();

            // StartTx~StopTx 사이 Charging 상태 체크
            boolean hasCharging = calls.stream()
                    .filter(m -> m.getTimestamp() != null
                            && m.getTimestamp().isAfter(stTs)
                            && m.getTimestamp().isBefore(stpTs))
                    .anyMatch(m -> "StatusNotification".equals(m.getAction())
                            && m.getPayloadDetail() != null
                            && "Charging".equals(m.getPayloadDetail().get("status")));
            if (!hasCharging) {
                addViolation(violations, "WARN", stTs,
                        sessionLabel + ": StartTransaction 후 StatusNotification(Charging) 없음",
                        chargerId, txId, null);
            }
        }
    }

    /**
     * 세션 시작 신호 판별
     * - StatusNotification(Preparing): 커넥터가 준비 상태로 전환
     * - Authorize: 사용자 인증 (RFID 등)
     * - RemoteStartTransaction: CS에서 원격 충전 시작 명령 (CS→CP)
     */
    private boolean isSessionStartSignal(OcppMessage m) {
        if ("StatusNotification".equals(m.getAction())
                && m.getPayloadDetail() != null
                && "Preparing".equals(m.getPayloadDetail().get("status"))) {
            return true;
        }
        return "Authorize".equals(m.getAction())
                || "RemoteStartTransaction".equals(m.getAction());
    }

    /**
     * 시작 신호(Preparing/Authorize/RemoteStartTransaction)가 확인된 완전한 세션 건수 반환
     */
    private int countCompleteSessionStarts(List<OcppMessage> messages) {
        List<OcppMessage> startTxList = messages.stream()
                .filter(OcppMessage::isCall)
                .filter(m -> "StartTransaction".equals(m.getAction()) && m.getTimestamp() != null)
                .collect(Collectors.toList());

        int count = 0;
        for (OcppMessage st : startTxList) {
            LocalDateTime windowStart = st.getTimestamp().minusMinutes(10);
            boolean hasSignal = messages.stream()
                    .filter(m -> m.getTimestamp() != null
                            && !m.getTimestamp().isBefore(windowStart)
                            && m.getTimestamp().isBefore(st.getTimestamp()))
                    .anyMatch(this::isSessionStartSignal);
            if (hasSignal) count++;
        }
        return count;
    }

    private void addViolation(List<FlowViolation> violations, String severity,
                               LocalDateTime ts, String message, String chargerId,
                               String transactionId, String messageId) {
        FlowViolation v = new FlowViolation();
        v.setSeverity(severity);
        v.setChargerId(chargerId);
        v.setTransactionId(transactionId);
        v.setMessageId(messageId);
        v.setTimestamp(ts != null ? ts.format(TS_FMT) : "");
        v.setMessage(message);
        violations.add(v);
    }
}
