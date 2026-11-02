-- Run after fresh bootstrap or existing-data migration.
-- Aggregate only: no patient names or direct identifiers in the BI export.
CREATE OR REPLACE VIEW reporting_doctor_day AS
SELECT service_date, doctor_id,
  COUNT(*) AS check_ins,
  COUNT(*) FILTER(WHERE status='COMPLETED') AS completed,
  COUNT(*) FILTER(WHERE status='NO_SHOW') AS no_shows,
  ROUND((AVG((EXTRACT(EPOCH FROM (consultation_start_time-check_in_time))/60.0))
   FILTER(WHERE consultation_start_time>=check_in_time))::numeric,2) AS avg_wait_minutes,
  PERCENTILE_CONT(0.9) WITHIN GROUP(ORDER BY EXTRACT(EPOCH FROM (consultation_start_time-check_in_time))/60.0)
    FILTER(WHERE consultation_start_time>=check_in_time) AS p90_wait_minutes
FROM queue_entry GROUP BY service_date,doctor_id;
-- A separate DB reporting role should have SELECT on this view only (deployment decision).
