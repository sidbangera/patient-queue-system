package com.patientqueue.system.controller;

import com.patientqueue.system.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsControllerTest {
    private final AnalyticsService service = mock(AnalyticsService.class);
    private final AnalyticsController controller = new AnalyticsController(service, "Asia/Kolkata");
    private final LocalDate from = LocalDate.of(2026, 10, 20);
    private final LocalDate to = LocalDate.of(2026, 10, 26);

    @Test void summaryUsesExplicitDateAndDoctorFilters() {
        when(service.summary(from, to, 7L)).thenReturn(Map.of("total", 2L));
        assertEquals(2L, controller.summary(from, to, 7L).get("total"));
        verify(service).summary(from, to, 7L);
    }

    @Test void dailySupportsAllDoctors() {
        when(service.daily(from, to, null)).thenReturn(List.of());
        assertEquals(List.of(), controller.daily(from, to, null));
        verify(service).daily(from, to, null);
    }

    @Test void exportContainsAttachmentHeadersAndUsesFilters() {
        when(service.exportDoctorDayCsv(from, to, null)).thenReturn("service_date,doctor_id\r\n");
        var response = controller.exportCsv(from, to, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains(".csv"));
        assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
        assertEquals("service_date,doctor_id\r\n", response.getBody());
    }

    @Test void rejectsReversedDates() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.hourly(to, from, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test void rejectsOverlongRange() {
        assertThrows(ResponseStatusException.class,
                () -> controller.doctors(LocalDate.of(2025, 1, 1), to, null));
        verifyNoInteractions(service);
    }

    @Test void rejectsInvalidDoctorId() {
        assertThrows(ResponseStatusException.class,
                () -> controller.summary(from, to, 0L));
        verifyNoInteractions(service);
    }

    @Test void comparisonRespectsDateAndDoctorFilters() {
        when(service.comparison(from, to, 4L)).thenReturn(Map.of("checkinsDelta", 1L));
        assertEquals(1L, controller.comparison(from, to, 4L).get("checkinsDelta"));
        verify(service).comparison(from, to, 4L);
    }

    @Test void qualityRespectsDateAndDoctorFilters() {
        when(service.dataQuality(from, to, null)).thenReturn(Map.of("flaggedVisits", 0L));
        assertEquals(0L, controller.quality(from, to, null).get("flaggedVisits"));
        verify(service).dataQuality(from, to, null);
    }

    @Test void comparisonRejectsInvalidRangeBeforeQuerying() {
        assertThrows(ResponseStatusException.class, () -> controller.comparison(to, from, 1L));
        verifyNoInteractions(service);
    }

    @Test void qualityRejectsNonPositiveDoctorId() {
        assertThrows(ResponseStatusException.class, () -> controller.quality(from, to, -3L));
        verifyNoInteractions(service);
    }

}
