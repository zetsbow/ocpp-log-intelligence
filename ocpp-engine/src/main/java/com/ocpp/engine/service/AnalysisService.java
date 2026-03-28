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
import org.springframework.web.client.RestTemplate;

import java.net.URI;
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
    private final RestTemplate restTemplate;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 기능1·2: 로그 분석 메인 로직
     */
    @Transactional
    public AnalysisResult analyze(AnalyzeRequest request) {
        log.info("분석 시작 - 충전기: {}, 파일: {}", request.getChargerId(), request.getFileName());

        // 0. fileUrl로 HTTP GET → logContent 세팅 (logContent가 없을 때)
        if ((request.getLogContent() == null || request.getLogContent().isBlank())
                && request.getFileUrl() != null && !request.getFileUrl().isBlank()) {
            try {
                String content = restTemplate.getForObject(URI.create(request.getFileUrl()), String.class);
                if (content == null || content.isBlank())
                    throw new RuntimeException("파일 내용이 비어있습니다: " + request.getFileUrl());
                request.setLogContent(content);
                if (request.getFileName() == null) {
                    String url = request.getFileUrl();
                    request.setFileName(url.substring(url.lastIndexOf('/') + 1));
                }
                log.info("[AnalysisService] fileUrl 다운로드 완료: {} bytes", content.length());
            } catch (Exception e) {
                throw new RuntimeException("로그 파일 URL 호출 실패: " + request.getFileUrl() + " → " + e.getMessage(), e);
            }
        }

        // 1. 파싱
        List<OcppMessage> allMessages = parser.parse(request.getLogContent());

        // 2. 충전기 ID + 시간 필터링
        List<OcppMessage> filtered = filterMessages(allMessages, request);

        // 3. 패턴 매칭 (기존 FaultPattern 기반)
        List<FaultDetection> detections = patternMatcher.match(filtered);

        // 4. 세션 ID: web에서 전달한 값 우선, 없으면 파일명에서 추출
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : extractSessionId(request.getFileName());
        log.info("[AnalysisService] sessionId={} (fileName={})", sessionId, request.getFileName());

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
        for (int i = 0; i < flowEntries.size(); i += 1_000) {
            ocppFlowEntryMapper.insertAll(flowEntries.subList(i, Math.min(i + 1_000, flowEntries.size())));
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

        // ── Pre-scan 1: Call uniqueId → action 매핑 ──────────────────────────
        Map<String, String> uidToAction = new HashMap<>();     // uniqueId → raw action
        Map<String, String> uidToDisplay = new HashMap<>();    // uniqueId → displayAction
        for (OcppMessage msg : messages) {
            if (msg.isCall() && msg.getUniqueId() != null) {
                uidToAction.put(msg.getUniqueId(), msg.getAction());
                uidToDisplay.put(msg.getUniqueId(), msg.getDisplayAction());
            }
        }

        // ── Pre-scan 2: StartTransaction CallResult uid → transactionId 매핑 ─
        // StartTransaction CallResult payload: {"transactionId": 123, "idTagInfo":{...}}
        Map<String, String> uidToTxId = new HashMap<>();
        for (OcppMessage msg : messages) {
            if (msg.isCallResult()
                    && msg.getUniqueId() != null
                    && msg.getTransactionId() != null
                    && "StartTransaction".equals(uidToAction.get(msg.getUniqueId()))) {
                uidToTxId.put(msg.getUniqueId(), msg.getTransactionId());
            }
        }

        // ── Pre-scan 3: StartTransaction Call uniqueId → transactionId 역매핑 ─
        // StartTransaction Call 행 자체에도 txId를 붙이기 위해
        // (Call의 uniqueId == CallResult의 uniqueId 이므로 동일 키 사용)

        List<OcppFlowEntry> entries = new ArrayList<>();
        String activeTxId = null;   // 현재 진행 중인 transactionId

        for (OcppMessage msg : messages) {
            OcppFlowEntry e = new OcppFlowEntry();
            e.setMessageId(msg.getUniqueId());
            e.setTimestamp(msg.getTimestamp() != null ? msg.getTimestamp().format(TS_FMT) : "");
            e.setChargerId(msg.getChargerId() != null ? msg.getChargerId() : fallbackChargerId);

            // 이 메시지의 action (Call이면 직접, CallResult/Error이면 uidToAction으로 역조회)
            String rawAction = msg.isCall()
                    ? msg.getAction()
                    : uidToAction.getOrDefault(msg.getUniqueId(), "");

            if (msg.isCall()) {
                // ── Call 처리 ──────────────────────────────────────────────
                e.setAction(msg.getDisplayAction());
                if (e.getAction() == null || e.getAction().isBlank()) continue;
                e.setMessageType("Call");
                e.setDirection("CP→CS");
                e.setDetailText(toDetailText(msg.getPayloadDetail()));

                if ("StartTransaction".equals(rawAction)) {
                    // StartTransaction Call: transactionId는 pre-scan으로 확보
                    String txId = uidToTxId.get(msg.getUniqueId());
                    e.setTransactionId(txId);
                } else if ("StopTransaction".equals(rawAction)) {
                    // StopTransaction에는 payload에 transactionId 있음
                    e.setTransactionId(msg.getTransactionId() != null
                            ? msg.getTransactionId() : activeTxId);
                } else {
                    e.setTransactionId(activeTxId);
                }

            } else if (msg.isCallResult()) {
                // ── CallResult 처리 ────────────────────────────────────────
                String displayAction = uidToDisplay.get(msg.getUniqueId());
                if (displayAction == null) continue;
                e.setAction(displayAction);
                e.setMessageType("CallResult");
                e.setDirection("CS→CP");
                e.setStatus(msg.getStatus());

                if ("StartTransaction".equals(rawAction)) {
                    // StartTransaction CallResult: transactionId 확정 → activeTxId 갱신
                    String txId = msg.getTransactionId();
                    if (txId != null) {
                        activeTxId = txId;
                        uidToTxId.put(msg.getUniqueId(), txId);   // 재확인용 갱신
                    }
                    e.setTransactionId(activeTxId);
                    // detailText에 transactionId 표시
                    if (activeTxId != null) e.setDetailText("transactionId=" + activeTxId);

                } else if ("StopTransaction".equals(rawAction)) {
                    e.setTransactionId(activeTxId);
                    activeTxId = null;   // 트랜잭션 종료

                } else {
                    e.setTransactionId(activeTxId);
                }

            } else {
                // ── CallError 처리 ─────────────────────────────────────────
                String displayAction = uidToDisplay.get(msg.getUniqueId());
                if (displayAction == null) continue;
                e.setAction(displayAction);
                e.setMessageType("CallError");
                e.setDirection("CS→CP");
                e.setStatus(msg.getStatus());
                e.setTransactionId(activeTxId);
                e.setIsFault("Y");  // CallError는 항상 장애
            }

            // StatusNotification Faulted → 장애 마킹
            if ("StatusNotification".equals(e.getAction())
                    && msg.getPayloadDetail() != null
                    && "Faulted".equals(msg.getPayloadDetail().get("status"))) {
                e.setIsFault("Y");
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
        // BATCH 모드에서 충전기별로 분리 처리 (혼용 방지)
        if (chargerId == null) {
            calls.stream()
                    .map(OcppMessage::getChargerId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(cid -> {
                        List<OcppMessage> cidCalls = calls.stream()
                                .filter(m -> cid.equals(m.getChargerId()))
                                .collect(Collectors.toList());
                        List<OcppMessage> cidAllMessages = allMessages.stream()
                                .filter(m -> cid.equals(m.getChargerId()))
                                .collect(Collectors.toList());
                        doCheckHeartbeatInterval(cidCalls, violations, cid, cidAllMessages, uidToDisplay);
                    });
        } else {
            doCheckHeartbeatInterval(calls, violations, chargerId, allMessages, uidToDisplay);
        }
    }

    private void doCheckHeartbeatInterval(List<OcppMessage> calls, List<FlowViolation> violations,
                                           String chargerId, List<OcppMessage> allMessages,
                                           Map<String, String> uidToDisplay) {
        List<OcppMessage> heartbeats = calls.stream()
                .filter(m -> "Heartbeat".equals(m.getAction()) && m.getTimestamp() != null)
                .collect(Collectors.toList());

        for (int i = 1; i < heartbeats.size(); i++) {
            long gapMin = ChronoUnit.MINUTES.between(
                    heartbeats.get(i - 1).getTimestamp(),
                    heartbeats.get(i).getTimestamp());
            if (gapMin > 15) {
                LocalDateTime ts = heartbeats.get(i).getTimestamp();
                addViolation(violations, "WARN", ts,
                        String.format("Heartbeat 간격 초과 (15분): %s → %s (%d분)",
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
                    String effectiveChargerId = chargerId != null ? chargerId : m.getChargerId();
                    addViolation(violations, "ERROR", m.getTimestamp(), msg, effectiveChargerId,
                            findActiveTxId(allMessages, uidToDisplay, m.getTimestamp()), m.getUniqueId());
                });
    }

    /** 충전 세션(StartTx→StopTx)별 KEVIT 흐름 검증 */
    private void checkChargingSessionFlow(List<OcppMessage> calls, List<FlowViolation> violations,
                                           String chargerId, Map<String, String> uidToTxId,
                                           List<OcppMessage> allMessages) {
        // BATCH 모드에서 충전기별로 분리 처리 (혼용 방지)
        if (chargerId == null) {
            calls.stream()
                    .map(OcppMessage::getChargerId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(cid -> {
                        List<OcppMessage> cidCalls = calls.stream()
                                .filter(m -> cid.equals(m.getChargerId()))
                                .collect(Collectors.toList());
                        List<OcppMessage> cidAllMessages = allMessages.stream()
                                .filter(m -> cid.equals(m.getChargerId()))
                                .collect(Collectors.toList());
                        doCheckChargingSessionFlow(cidCalls, violations, cid, uidToTxId, cidAllMessages);
                    });
        } else {
            doCheckChargingSessionFlow(calls, violations, chargerId, uidToTxId, allMessages);
        }
    }

    private void doCheckChargingSessionFlow(List<OcppMessage> calls, List<FlowViolation> violations,
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
            String txId = uidToTxId.get(st.getUniqueId());

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
                // StartTx 이후 Charging 외 StatusNotification이 없으면 로그 끝까지 충전 중 → 위반 아님
                boolean hasNonChargingStatus = calls.stream()
                        .filter(m -> m.getTimestamp() != null
                                && m.getTimestamp().isAfter(stTs)
                                && (nextStTs == null || m.getTimestamp().isBefore(nextStTs)))
                        .anyMatch(m -> "StatusNotification".equals(m.getAction())
                                && m.getPayloadDetail() != null
                                && !"Charging".equals(m.getPayloadDetail().get("status")));
                if (!hasNonChargingStatus) continue;

                addViolation(violations, "WARN", stTs,
                        sessionLabel + ": 대응하는 StopTransaction 없음",
                        chargerId, txId, null);
                continue;
            }

            LocalDateTime stpTs = stp.getTimestamp();

            // StartTx~StopTx 사이 Charging 상태 체크
            boolean hasCharging = calls.stream()
                    .filter(m -> m.getTimestamp() != null
                            && !m.getTimestamp().isBefore(stTs)
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
