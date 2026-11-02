package com.patientqueue.system.controller;

import com.patientqueue.system.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    private final ZoneId clinicZone;

    public AnalyticsController(AnalyticsService service,
                               @Value("${app.clinic-zone:Asia/Kolkata}") String clinicZone) {
        this.service = service;
        this.clinicZone = ZoneId.of(clinicZone);
    }

    private record Window(LocalDate from, LocalDate to, Long doctorId) {}

    private Window window(LocalDate from, LocalDate to, Long doctorId) {
        LocalDate today = LocalDate.now(clinicZone);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        long duration = ChronoUnit.DAYS.between(start, end);
        if (duration < 0 || duration > 365 || start.getYear() < 1900 || end.getYear() > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select a valid date range of at most 366 inclusive days");
        }
        if (doctorId != null && doctorId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "doctorId must be positive");
        }
        return new Window(start, end, doctorId);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        Map<String, Object> report = service.dashboard(w.from, w.to, w.doctorId);
        report.put("clinicToday", LocalDate.now(clinicZone).toString());
        report.put("clinicZone", clinicZone.toString());
        return report;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        return service.summary(w.from, w.to, w.doctorId);
    }

    @GetMapping("/daily")
    public List<Map<String, Object>> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        return service.daily(w.from, w.to, w.doctorId);
    }

    @GetMapping("/doctors")
    public List<Map<String, Object>> doctors(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        return service.doctors(w.from, w.to, w.doctorId);
    }

    @GetMapping("/hourly")
    public List<Map<String, Object>> hourly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        return service.hourly(w.from, w.to, w.doctorId);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        String csv = service.exportDoctorDayCsv(w.from, w.to, w.doctorId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pqueue-doctor-day.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    /** Like-for-like comparison with the immediately preceding date range. */
    @GetMapping("/comparison")
    public Map<String, Object> comparison(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        return service.comparison(w.from, w.to, w.doctorId);
    }

    /** Aggregate validation indicators; ADMIN-only under SecurityConfig. */
    @GetMapping("/quality")
    public Map<String, Object> quality(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId) {
        Window w = window(from, to, doctorId);
        return service.dataQuality(w.from, w.to, w.doctorId);
    }

}
