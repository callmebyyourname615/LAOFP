package com.example.switching.rtp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.switching.rtp.enums.RtpStatus;
import com.example.switching.rtp.exception.RtpInvalidTransitionException;
import com.example.switching.rtp.service.RtpStateMachine;

class RtpStateMachineTest {

    private final RtpStateMachine stateMachine = new RtpStateMachine();

    @Test
    void pendingAuthAllowsAuthoriseDeclineExpireAndCancel() {
        assertTrue(stateMachine.allowedTargets(RtpStatus.PENDING_AUTH).contains(RtpStatus.AUTHORISED));
        assertTrue(stateMachine.allowedTargets(RtpStatus.PENDING_AUTH).contains(RtpStatus.DECLINED));
        assertTrue(stateMachine.allowedTargets(RtpStatus.PENDING_AUTH).contains(RtpStatus.EXPIRED));
        assertTrue(stateMachine.allowedTargets(RtpStatus.PENDING_AUTH).contains(RtpStatus.CANCELLED));
    }

    @Test
    void terminalStatesRejectFurtherTransitions() {
        for (RtpStatus terminal : new RtpStatus[] {
                RtpStatus.SETTLED,
                RtpStatus.DECLINED,
                RtpStatus.EXPIRED,
                RtpStatus.CANCELLED }) {
            assertTrue(stateMachine.isTerminal(terminal));
            assertThrows(RtpInvalidTransitionException.class,
                    () -> stateMachine.assertTransition(terminal, RtpStatus.PENDING_AUTH));
        }
        assertFalse(stateMachine.isTerminal(RtpStatus.PENDING_AUTH));
    }

    @Test
    void pendingCannotJumpDirectlyToSettled() {
        assertThrows(RtpInvalidTransitionException.class,
                () -> stateMachine.assertTransition(RtpStatus.PENDING_AUTH, RtpStatus.SETTLED));
    }
}
