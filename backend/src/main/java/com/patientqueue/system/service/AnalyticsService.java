package com.patientqueue.system.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.Instant;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Privacy-conscious outpatient operational reporting. All reporting cohorts are selected
 * by service_date (the clinic-local date assigned at check-in), not by completion date.
 * Every external filter is passed as a JDBC bind parameter.
 */
@Service
public class AnalyticsService {
    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Map<String, Object> params(LocalDate from, LocalDate to, Long doctorId) {
        Map<String, Object> result = new HashMap<>();
        result.put("fromDate", from);
        result.put("toDate", to);
        if (doctorId != null) result.put("doctorId", doctorId);
        return result;
    }

    private String entryDoctorFilter(Long doctorId, String alias) {
        return doctorId == null ? "" : " AND " + alias + ".doctor_id = :doctorId\n";
    }

    public Map<String, Object> summary(LocalDate from, LocalDate to, Long doctorId) {
        String sql = """
            SELECT COUNT(*) AS total,
              COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
              COUNT(*) FILTER (WHERE status = 'NO_SHOW') AS no_shows,
              COUNT(*) FILTER (WHERE status = 'WAITING') AS waiting,
              COUNT(*) FILTER (WHERE status = 'IN_CONSULTATION') AS in_consultation,
              COUNT(*) FILTER (WHERE consultation_start_time >= check_in_time AND status IN ('IN_CONSULTATION','COMPLETED')) AS wait_samples,
              AVG(EXTRACT(EPOCH FROM (consultation_start_time - check_in_time)) / 60.0)
                FILTER (WHERE consultation_start_time >= check_in_time AND status IN ('IN_CONSULTATION','COMPLETED')) AS avg_wait,
              PERCENTILE_CONT(0.9) WITHIN GROUP (
                ORDER BY EXTRACT(EPOCH FROM (consultation_start_time - check_in_time)) / 60.0)
                FILTER (WHERE consultation_start_time >= check_in_time AND status IN ('IN_CONSULTATION','COMPLETED')) AS p90_wait,
              AVG(EXTRACT(EPOCH FROM (consultation_end_time - consultation_start_time)) / 60.0)
                FILTER (WHERE consultation_end_time >= consultation_start_time
                        AND consultation_start_time >= check_in_time
                        AND status = 'COMPLETED') AS avg_consultation
            FROM queue_entry q
            WHERE q.service_date BETWEEN :fromDate AND :toDate
            """ + entryDoctorFilter(doctorId, "q");

        return jdbc.queryForObject(sql, params(from, to, doctorId), (rs, i) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            long total = rs.getLong("total");
            long completed = rs.getLong("completed");
            long noShows = rs.getLong("no_shows");
            result.put("total", total);
            result.put("completed", completed);
            result.put("noShows", noShows);
            result.put("waiting", rs.getLong("waiting"));
            result.put("inConsultation", rs.getLong("in_consultation"));
            result.put("waitSampleSize", rs.getLong("wait_samples"));
            result.put("avgWaitMinutes", rs.getObject("avg_wait") == null ? null : rs.getDouble("avg_wait"));
            result.put("p90WaitMinutes", rs.getObject("p90_wait") == null ? null : rs.getDouble("p90_wait"));
            result.put("avgConsultationMinutes", rs.getObject("avg_consultation") == null
                    ? null : rs.getDouble("avg_consultation"));
            result.put("noShowRatePct", ReportingMath.rate(noShows, total));
            result.put("completionRatePct", ReportingMath.rate(completed, total));
            return result;
        });
    }

    /** Include zero-arrival days so daily charts are not misleading. */
    public List<Map<String, Object>> daily(LocalDate from, LocalDate to, Long doctorId) {
        String sql = """
            WITH days AS (
              SELECT CAST(g.ts AS date) AS day
              FROM generate_series(CAST(:fromDate AS timestamp), CAST(:toDate AS timestamp),
                                   INTERVAL '1 day') AS g(ts)
            )
            SELECT d.day AS service_date, COUNT(q.id) AS checkins,
              COUNT(q.id) FILTER (WHERE q.status = 'COMPLETED') AS completed,
              COUNT(q.id) FILTER (WHERE q.status = 'NO_SHOW') AS no_shows
            FROM days d
            LEFT JOIN queue_entry q ON q.service_date = d.day
            """ + entryDoctorFilter(doctorId, "q") + """
            GROUP BY d.day
            ORDER BY d.day
            """;
        return jdbc.query(sql, params(from, to, doctorId), (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", rs.getDate("service_date").toLocalDate().toString());
            row.put("checkins", rs.getLong("checkins"));
            row.put("completed", rs.getLong("completed"));
            row.put("noShows", rs.getLong("no_shows"));
            return row;
        });
    }

    /** Doctor workload; no patient identifiers, including for zero-workload doctors. */
    public List<Map<String, Object>> doctors(LocalDate from, LocalDate to, Long doctorId) {
        String sql = """
            SELECT d.id AS doctor_id, d.name AS doctor_name, d.department,
              COUNT(q.id) AS checkins,
              COUNT(q.id) FILTER (WHERE q.status = 'COMPLETED') AS completed,
              COUNT(q.id) FILTER (WHERE q.status = 'NO_SHOW') AS no_shows,
              COUNT(q.id) FILTER (WHERE q.status = 'WAITING') AS waiting,
              AVG(EXTRACT(EPOCH FROM (q.consultation_start_time - q.check_in_time)) / 60.0)
                FILTER (WHERE q.consultation_start_time >= q.check_in_time AND q.status IN ('IN_CONSULTATION','COMPLETED')) AS avg_wait
            FROM doctor d
            LEFT JOIN queue_entry q ON q.doctor_id = d.id
              AND q.service_date BETWEEN :fromDate AND :toDate
            """ + (doctorId == null ? "" : " WHERE d.id = :doctorId ") + """
            GROUP BY d.id, d.name, d.department
            ORDER BY checkins DESC, d.name ASC
            """;
        return jdbc.query(sql, params(from, to, doctorId), (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("doctorId", rs.getLong("doctor_id"));
            row.put("doctorName", rs.getString("doctor_name"));
            row.put("department", rs.getString("department"));
            row.put("checkins", rs.getLong("checkins"));
            row.put("completed", rs.getLong("completed"));
            row.put("noShows", rs.getLong("no_shows"));
            row.put("waiting", rs.getLong("waiting"));
            row.put("avgWaitMinutes", rs.getObject("avg_wait") == null ? null : rs.getDouble("avg_wait"));
            return row;
        });
    }

    /** Arrival hour of day, aggregated over the selected service-date cohort. */
    public List<Map<String, Object>> hourly(LocalDate from, LocalDate to, Long doctorId) {
        String sql = """
            SELECT h.hr AS arrival_hour, COUNT(q.id) AS checkins
            FROM generate_series(0, 23) AS h(hr)
            LEFT JOIN queue_entry q
              ON q.service_date BETWEEN :fromDate AND :toDate
              AND EXTRACT(HOUR FROM q.check_in_time) = h.hr
            """ + entryDoctorFilter(doctorId, "q") + """
            GROUP BY h.hr
            ORDER BY h.hr
            """;
        return jdbc.query(sql, params(from, to, doctorId), (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hour", rs.getInt("arrival_hour"));
            row.put("checkins", rs.getLong("checkins"));
            return row;
        });
    }

    /** Downloadable doctor/day aggregates, not patient-level data. */
    public String exportDoctorDayCsv(LocalDate from, LocalDate to, Long doctorId) {
        String sql = """
            SELECT service_date, doctor_id, COUNT(*) AS check_ins,
              COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
              COUNT(*) FILTER (WHERE status = 'NO_SHOW') AS no_shows,
              AVG(EXTRACT(EPOCH FROM (consultation_start_time - check_in_time))/60.0)
                FILTER (WHERE consultation_start_time >= check_in_time AND status IN ('IN_CONSULTATION','COMPLETED')) AS avg_wait_minutes,
              PERCENTILE_CONT(0.9) WITHIN GROUP (
                ORDER BY EXTRACT(EPOCH FROM (consultation_start_time - check_in_time))/60.0)
                FILTER (WHERE consultation_start_time >= check_in_time AND status IN ('IN_CONSULTATION','COMPLETED')) AS p90_wait_minutes
            FROM queue_entry q
            WHERE service_date BETWEEN :fromDate AND :toDate
            """ + entryDoctorFilter(doctorId, "q") + " GROUP BY service_date, doctor_id ORDER BY service_date, doctor_id";
        List<String> rows = jdbc.query(sql, params(from, to, doctorId), (rs, i) ->
                String.join(",",
                        rs.getDate("service_date").toLocalDate().toString(),
                        rs.getString("doctor_id"),
                        rs.getString("check_ins"),
                        rs.getString("completed"),
                        rs.getString("no_shows"),
                        rs.getString("avg_wait_minutes") == null ? "" : rs.getString("avg_wait_minutes"),
                        rs.getString("p90_wait_minutes") == null ? "" : rs.getString("p90_wait_minutes")));
        String header = "service_date,doctor_id,check_ins,completed,no_shows,avg_wait_minutes,p90_wait_minutes";
        return header + "\r\n" + (rows.isEmpty() ? "" : String.join("\r\n", rows) + "\r\n");
    }

    /**
     * Compare like-for-like, adjacent check-in cohorts. A seven-day window is
     * compared with the preceding seven days; the doctor filter is unchanged.
     * Rates without a denominator are returned as null, not a fabricated zero.
     */
    public Map<String, Object> comparison(LocalDate from, LocalDate to, Long doctorId) {
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = ReportingMath.previousStart(from, to);
        Map<String, Object> current = summary(from, to, doctorId);
        Map<String, Object> previous = summary(previousFrom, previousTo, doctorId);
        long currentTotal = ((Number) current.get("total")).longValue();
        long previousTotal = ((Number) previous.get("total")).longValue();
        long currentCompleted = ((Number) current.get("completed")).longValue();
        long previousCompleted = ((Number) previous.get("completed")).longValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentFrom", from.toString());
        result.put("currentTo", to.toString());
        result.put("previousFrom", previousFrom.toString());
        result.put("previousTo", previousTo.toString());
        result.put("current", current);
        result.put("previous", previous);
        result.put("checkinsDelta", currentTotal - previousTotal);
        result.put("completedDelta", currentCompleted - previousCompleted);
        // Percentage changes are intentionally not shown when a cohort is empty.
        result.put("checkinsChangePct", previousTotal == 0 ? null
                : ReportingMath.percentChange(currentTotal, previousTotal));
        result.put("completionRateDeltaPoints", previousTotal == 0 || currentTotal == 0 ? null
                : ((Number) current.get("completionRatePct")).doubleValue()
                  - ((Number) previous.get("completionRatePct")).doubleValue());
        return result;
    }

    /**
     * Read-only data quality indicators scoped by service date and doctor.
     * Does not expose individual patient or visit IDs and does not mutate data.
     * A record may contribute to multiple checks; flaggedVisits counts it once.
     */
    public Map<String, Object> dataQuality(LocalDate from, LocalDate to, Long doctorId) {
        String sql = """
            SELECT COUNT(*) AS records_checked,
              COUNT(*) FILTER (WHERE check_in_time IS NULL) AS missing_checkin,
              COUNT(*) FILTER (WHERE doctor_id IS NULL OR patient_id IS NULL) AS missing_reference,
              COUNT(*) FILTER (WHERE
                (status = 'IN_CONSULTATION' AND consultation_start_time IS NULL)
                OR (status IN ('WAITING','NO_SHOW') AND
                    (consultation_start_time IS NOT NULL OR consultation_end_time IS NOT NULL))
                OR (status = 'IN_CONSULTATION' AND consultation_end_time IS NOT NULL)) AS state_time_mismatch,
              COUNT(*) FILTER (WHERE check_in_time::date <> service_date) AS service_date_mismatch,
              COUNT(*) FILTER (WHERE consultation_start_time IS NOT NULL
                       AND consultation_start_time < check_in_time) AS negative_wait,
              COUNT(*) FILTER (WHERE consultation_start_time IS NOT NULL
                       AND consultation_end_time IS NOT NULL
                       AND consultation_end_time < consultation_start_time) AS negative_consultation,
              COUNT(*) FILTER (WHERE status = 'COMPLETED'
                       AND (consultation_start_time IS NULL OR consultation_end_time IS NULL))
                       AS completed_missing_timestamps,
              COUNT(*) FILTER (WHERE
                  (check_in_time IS NULL)
                  OR (doctor_id IS NULL OR patient_id IS NULL)
                  OR (status = 'IN_CONSULTATION' AND consultation_start_time IS NULL)
                  OR (status IN ('WAITING','NO_SHOW') AND
                      (consultation_start_time IS NOT NULL OR consultation_end_time IS NOT NULL))
                  OR (status = 'IN_CONSULTATION' AND consultation_end_time IS NOT NULL)
                  OR (check_in_time::date <> service_date)
                  OR (consultation_start_time IS NOT NULL
                       AND consultation_start_time < check_in_time)
                  OR (consultation_start_time IS NOT NULL
                       AND consultation_end_time IS NOT NULL
                       AND consultation_end_time < consultation_start_time)
                  OR (status = 'COMPLETED'
                       AND (consultation_start_time IS NULL OR consultation_end_time IS NULL)))
                       AS flagged_visits
            FROM queue_entry q
            WHERE q.service_date BETWEEN :fromDate AND :toDate
            """ + entryDoctorFilter(doctorId, "q");
        return jdbc.queryForObject(sql, params(from, to, doctorId), (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("missingReferences", rs.getLong("missing_reference"));
            row.put("missingCheckIn", rs.getLong("missing_checkin"));
            row.put("stateTimestampMismatch", rs.getLong("state_time_mismatch"));
            row.put("recordsChecked", rs.getLong("records_checked"));
            row.put("flaggedVisits", rs.getLong("flagged_visits"));
            row.put("serviceDateMismatch", rs.getLong("service_date_mismatch"));
            row.put("negativeWait", rs.getLong("negative_wait"));
            row.put("negativeConsultation", rs.getLong("negative_consultation"));
            row.put("completedMissingTimestamps", rs.getLong("completed_missing_timestamps"));
            return row;
        });
    }


    /** A single repeatable-read snapshot prevents contradictory cards during concurrent updates. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> dashboard(LocalDate from, LocalDate to, Long doctorId) {
        Map<String, Object> comparison = comparison(from, to, doctorId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString()); result.put("to", to.toString());
        result.put("asOf", Instant.now().toString());
        result.put("summary", comparison.get("current")); result.put("comparison", comparison);
        result.put("daily", daily(from, to, doctorId)); result.put("doctors", doctors(from, to, doctorId));
        result.put("hourly", hourly(from, to, doctorId)); result.put("quality", dataQuality(from, to, doctorId));
        return result;
    }
}
