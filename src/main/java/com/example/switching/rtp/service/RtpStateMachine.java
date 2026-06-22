package com.example.switching.rtp.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.exception.RtpInvalidTransitionException;

@Component
public class RtpStateMachine {

    private static final Map<RtpStatus, Set<RtpStatus>> ALLOWED = buildAllowedTransitions();
    private static final Set<RtpStatus> TERMINAL = EnumSet.of(
            RtpStatus.SETTLED,
            RtpStatus.DECLINED,
            RtpStatus.EXPIRED,
            RtpStatus.CANCELLED);

    public void assertTransition(RtpStatus from, RtpStatus to) {
        if (from == null || to == null || !ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RtpInvalidTransitionException(from, to);
        }
    }

    public boolean isTerminal(RtpStatus status) {
        return status != null && TERMINAL.contains(status);
    }

    public Set<RtpStatus> allowedTargets(RtpStatus status) {
        return Set.copyOf(ALLOWED.getOrDefault(status, Set.of()));
    }

    private static Map<RtpStatus, Set<RtpStatus>> buildAllowedTransitions() {
        EnumMap<RtpStatus, Set<RtpStatus>> transitions = new EnumMap<>(RtpStatus.class);
        transitions.put(RtpStatus.PENDING_AUTH, EnumSet.of(
                RtpStatus.AUTHORISED,
                RtpStatus.DECLINED,
                RtpStatus.EXPIRED,
                RtpStatus.CANCELLED));
        transitions.put(RtpStatus.AUTHORISED, EnumSet.of(
                RtpStatus.SETTLED,
                RtpStatus.PARTIALLY_SETTLED,
                RtpStatus.INSTALMENT_IN_PROGRESS));
        transitions.put(RtpStatus.PARTIALLY_SETTLED, EnumSet.of(
                RtpStatus.SETTLED,
                RtpStatus.CANCELLED,
                RtpStatus.EXPIRED));
        transitions.put(RtpStatus.INSTALMENT_IN_PROGRESS, EnumSet.of(
                RtpStatus.SETTLED,
                RtpStatus.CANCELLED,
                RtpStatus.EXPIRED));
        transitions.put(RtpStatus.SETTLED, EnumSet.noneOf(RtpStatus.class));
        transitions.put(RtpStatus.DECLINED, EnumSet.noneOf(RtpStatus.class));
        transitions.put(RtpStatus.EXPIRED, EnumSet.noneOf(RtpStatus.class));
        transitions.put(RtpStatus.CANCELLED, EnumSet.noneOf(RtpStatus.class));
        return Map.copyOf(transitions);
    }
}
