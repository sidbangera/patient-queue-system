package com.patientqueue.system.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class ReportingMathTest {
    @Test void ratesRequireDenominators() {
        assertNull(ReportingMath.rate(0,0)); assertEquals(50.0, ReportingMath.rate(1,2));
    }
    @Test void relativeChangeAndPercentagePointsAreDifferent() {
        assertNull(ReportingMath.percentChange(4,0));
        assertEquals(100.0, ReportingMath.percentChange(4,2));
        assertEquals(-100.0, ReportingMath.percentChange(0,2));
    }
    @Test void previousPeriodHasEqualInclusiveLength() {
        assertEquals(LocalDate.of(2026,10,18), ReportingMath.previousStart(LocalDate.of(2026,10,25), LocalDate.of(2026,10,31)));
        assertEquals(LocalDate.of(2026,10,30), ReportingMath.previousStart(LocalDate.of(2026,10,31), LocalDate.of(2026,10,31)));
    }
    @Test void reversedAndOversizedWindowsAreRejected() {
        LocalDate from=LocalDate.of(2026,1,1);
        assertThrows(IllegalArgumentException.class, () -> ReportingMath.previousStart(from,from.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> ReportingMath.previousStart(from,from.plusDays(366)));
    }
}
