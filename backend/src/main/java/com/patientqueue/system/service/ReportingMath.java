package com.patientqueue.system.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure, framework-independent reporting definitions used by the service and tests. */
public final class ReportingMath {
    private ReportingMath() {}
    public static Double rate(long part, long total) {
        return total == 0 ? null : 100.0 * part / total;
    }
    public static Double percentChange(long current, long previous) {
        return previous == 0 ? null : 100.0 * (current - previous) / previous;
    }
    public static LocalDate previousStart(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || days > 366) throw new IllegalArgumentException("Invalid reporting window");
        return from.minusDays(days);
    }
}
