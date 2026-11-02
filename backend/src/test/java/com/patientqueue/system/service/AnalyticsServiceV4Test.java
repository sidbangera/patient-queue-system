package com.patientqueue.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests date cohort arithmetic and empty comparison denominator handling. */
class AnalyticsServiceV4Test {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AnalyticsService service = spy(new AnalyticsService(jdbc));

    @Test void comparisonUsesEqualLengthPrecedingWindowAndSameDoctor() {
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 26);
        LocalDate prevFrom = LocalDate.of(2026, 10, 13);
        LocalDate prevTo = LocalDate.of(2026, 10, 19);
        doReturn(Map.of("total", 2L, "completed", 1L, "completionRatePct", 50.0))
                .when(service).summary(from, to, 3L);
        doReturn(Map.of("total", 1L, "completed", 1L, "completionRatePct", 100.0))
                .when(service).summary(prevFrom, prevTo, 3L);

        Map<String, Object> result = service.comparison(from, to, 3L);
        assertEquals("2026-10-13", result.get("previousFrom"));
        assertEquals("2026-10-19", result.get("previousTo"));
        assertEquals(1L, result.get("checkinsDelta"));
        assertEquals(0L, result.get("completedDelta"));
        assertEquals(100.0, (Double) result.get("checkinsChangePct"), 0.001);
        assertEquals(-50.0, (Double) result.get("completionRateDeltaPoints"), 0.001);
        verify(service).summary(prevFrom, prevTo, 3L);
        verify(service).summary(from, to, 3L);
    }

    @Test void previousZeroDoesNotInventPercentChange() {
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 26);
        doReturn(Map.of("total", 2L, "completed", 1L, "completionRatePct", 50.0))
                .when(service).summary(from, to, null);
        doReturn(Map.of("total", 0L, "completed", 0L, "completionRatePct", 0.0))
                .when(service).summary(from.minusDays(7), from.minusDays(1), null);
        Map<String, Object> result = service.comparison(from, to, null);
        assertNull(result.get("checkinsChangePct"));
        assertNull(result.get("completionRateDeltaPoints"));
    }
}
