package com.patientqueue.system.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueueStatus State Machine Unit Tests")
class QueueStatusTest {

    @Test
    @DisplayName("WAITING can transition to IN_CONSULTATION and NO_SHOW")
    void waitingValidTransitions() {
        assertTrue(QueueStatus.WAITING.canTransitionTo(QueueStatus.IN_CONSULTATION),
                "WAITING should be able to transition to IN_CONSULTATION");
        assertTrue(QueueStatus.WAITING.canTransitionTo(QueueStatus.NO_SHOW),
                "WAITING should be able to transition to NO_SHOW");
    }

    @Test
    @DisplayName("WAITING cannot transition directly to COMPLETED or WAITING")
    void waitingInvalidTransitions() {
        assertFalse(QueueStatus.WAITING.canTransitionTo(QueueStatus.COMPLETED),
                "WAITING must not transition directly to COMPLETED without consultation");
        assertFalse(QueueStatus.WAITING.canTransitionTo(QueueStatus.WAITING),
                "WAITING must not transition to itself");
    }

    @Test
    @DisplayName("IN_CONSULTATION can only transition to COMPLETED")
    void inConsultationValidTransition() {
        assertTrue(QueueStatus.IN_CONSULTATION.canTransitionTo(QueueStatus.COMPLETED),
                "IN_CONSULTATION should transition to COMPLETED");
    }

    @Test
    @DisplayName("IN_CONSULTATION cannot transition to WAITING, NO_SHOW, or itself")
    void inConsultationInvalidTransitions() {
        assertFalse(QueueStatus.IN_CONSULTATION.canTransitionTo(QueueStatus.WAITING),
                "IN_CONSULTATION cannot revert to WAITING");
        assertFalse(QueueStatus.IN_CONSULTATION.canTransitionTo(QueueStatus.NO_SHOW),
                "IN_CONSULTATION cannot transition to NO_SHOW (patient was present)");
        assertFalse(QueueStatus.IN_CONSULTATION.canTransitionTo(QueueStatus.IN_CONSULTATION),
                "IN_CONSULTATION cannot transition to itself");
    }

    @ParameterizedTest
    @EnumSource(QueueStatus.class)
    @DisplayName("COMPLETED is a terminal state and cannot transition to any state")
    void completedIsTerminal(QueueStatus targetState) {
        assertFalse(QueueStatus.COMPLETED.canTransitionTo(targetState),
                "COMPLETED is terminal and must not transition to " + targetState);
    }

    @ParameterizedTest
    @EnumSource(QueueStatus.class)
    @DisplayName("NO_SHOW is a terminal state and cannot transition to any state")
    void noShowIsTerminal(QueueStatus targetState) {
        assertFalse(QueueStatus.NO_SHOW.canTransitionTo(targetState),
                "NO_SHOW is terminal and must not transition to " + targetState);
    }
}
