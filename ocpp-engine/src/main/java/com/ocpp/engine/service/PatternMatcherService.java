package com.ocpp.engine.service;

import com.ocpp.engine.dto.FaultDetection;
import com.ocpp.engine.dto.FaultPattern;
import com.ocpp.engine.dto.OcppMessage;
import com.ocpp.engine.mapper.FaultPatternMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 기능2·3: 룰 기반 장애 패턴 매처
 * - 등록된 FaultPattern을 기준으로 OCPP 메시지 슬라이딩 윈도우 탐지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatternMatcherService {

    private final FaultPatternMapper patternMapper;

    /**
     * 메시지 리스트에서 등록된 모든 패턴과 매칭하여 탐지 결과 반환
     */
    public List<FaultDetection> match(List<OcppMessage> messages) {
        List<FaultPattern> patterns = patternMapper.findAllEnabled();
        if (patterns.isEmpty()) {
            log.info("활성화된 패턴 없음");
            return Collections.emptyList();
        }

        List<FaultDetection> detections = new ArrayList<>();

        // uniqueId → Call 메시지 매핑 (응답 매칭용)
        Map<String, OcppMessage> callMap = new LinkedHashMap<>();
        for (OcppMessage msg : messages) {
            if (msg.isCall()) callMap.put(msg.getUniqueId(), msg);
        }

        // Call + CallResult 페어링
        List<MessagePair> pairs = new ArrayList<>();
        for (OcppMessage msg : messages) {
            if (msg.isCallResult() && callMap.containsKey(msg.getUniqueId())) {
                pairs.add(new MessagePair(callMap.get(msg.getUniqueId()), msg));
            }
        }

        for (FaultPattern pattern : patterns) {
            detections.addAll(matchPattern(pattern, pairs, messages));
        }

        log.info("패턴 매칭 완료 - {}개 패턴, {}개 탐지", patterns.size(), detections.size());
        return detections;
    }

    private List<FaultDetection> matchPattern(FaultPattern pattern,
                                               List<MessagePair> pairs,
                                               List<OcppMessage> allMessages) {
        List<FaultDetection> result = new ArrayList<>();

        // 1단계: triggerAction + triggerStatus 조건에 맞는 트리거 찾기
        List<MessagePair> triggerPairs = pairs.stream()
                .filter(p -> pattern.getTriggerAction().equalsIgnoreCase(p.call.getAction()))
                .filter(p -> pattern.getTriggerStatus() == null
                        || pattern.getTriggerStatus().equalsIgnoreCase(p.result.getStatus()))
                .collect(Collectors.toList());

        if (triggerPairs.isEmpty()) return result;

        // 2단계: 트리거 이후 withinSeconds 내에 followAction 존재 여부 확인
        for (MessagePair trigger : triggerPairs) {
            LocalDateTime triggerTime = trigger.result.getTimestamp() != null
                    ? trigger.result.getTimestamp() : LocalDateTime.now();
            String txId = trigger.call.getTransactionId();
            String chargerId = trigger.call.getChargerId();

            // 트리거 이후 메시지 중 followAction 검색
            Optional<OcppMessage> follow = allMessages.stream()
                    .filter(m -> m.isCall())
                    .filter(m -> pattern.getFollowAction().equalsIgnoreCase(m.getAction()))
                    .filter(m -> {
                        LocalDateTime t = m.getTimestamp() != null ? m.getTimestamp() : LocalDateTime.now();
                        long diff = ChronoUnit.SECONDS.between(triggerTime, t);
                        return diff >= 0 && diff <= pattern.getWithinSeconds();
                    })
                    .filter(m -> txId == null || txId.isBlank()
                            || txId.equals(m.getTransactionId())
                            || chargerId.equals(m.getChargerId()))
                    .findFirst();

            if (follow.isPresent()) {
                FaultDetection detection = new FaultDetection();
                detection.setChargerId(chargerId != null ? chargerId : "UNKNOWN");
                detection.setPatternId(pattern.getId());
                detection.setPatternName(pattern.getName());
                detection.setTransactionId(txId);
                detection.setDetectedAt(LocalDateTime.now());
                detection.setTriggerMsg(trigger.call.getRawLine());
                detection.setFollowMsg(follow.get().getRawLine());
                detection.setSeverity(pattern.getSeverity());
                detection.setDetail(String.format(
                        "[%s] %s → %s 감지 (트리거: %s / 응답: %s)",
                        pattern.getSeverity(), pattern.getTriggerAction(),
                        pattern.getFollowAction(), trigger.call.getUniqueId(),
                        trigger.result.getStatus()));
                result.add(detection);
                log.warn("장애 탐지: 패턴=[{}] 충전기=[{}] TX=[{}]",
                        pattern.getName(), chargerId, txId);
            }
        }
        return result;
    }

    private record MessagePair(OcppMessage call, OcppMessage result) {}
}
