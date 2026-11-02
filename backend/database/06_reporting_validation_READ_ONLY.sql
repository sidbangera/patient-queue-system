-- READ ONLY: choose exactly the same cohort as the dashboard.
-- Change these dates and optional doctor ID intentionally; no migration/seeding is performed.
\set from_date '2026-10-25'
\set to_date '2026-10-31'
\set doctor_id 0
-- doctor_id=0 means all doctors for THIS validation script only.
BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Kolkata';
WITH cohort AS (
 SELECT * FROM queue_entry
 WHERE service_date BETWEEN :'from_date'::date AND :'to_date'::date
   AND (:doctor_id = 0 OR doctor_id = :doctor_id)
)
SELECT COUNT(*) AS checkins,
 COUNT(*) FILTER (WHERE status='COMPLETED') AS completed,
 COUNT(*) FILTER (WHERE status='NO_SHOW') AS no_shows,
 COUNT(*) FILTER (WHERE status='WAITING') AS waiting,
 COUNT(*) FILTER (WHERE status='IN_CONSULTATION') AS in_consultation,
 100.0*COUNT(*) FILTER(WHERE status='COMPLETED')/NULLIF(COUNT(*),0) AS completion_pct,
 100.0*COUNT(*) FILTER(WHERE status='NO_SHOW')/NULLIF(COUNT(*),0) AS no_show_pct,
 COUNT(*) FILTER(WHERE status IN ('IN_CONSULTATION','COMPLETED') AND consultation_start_time>=check_in_time) AS valid_wait_samples,
 AVG(EXTRACT(EPOCH FROM consultation_start_time-check_in_time)/60.0)
   FILTER(WHERE status IN ('IN_CONSULTATION','COMPLETED') AND consultation_start_time>=check_in_time) AS avg_wait_minutes,
 PERCENTILE_CONT(0.9) WITHIN GROUP(ORDER BY EXTRACT(EPOCH FROM consultation_start_time-check_in_time)/60.0)
   FILTER(WHERE status IN ('IN_CONSULTATION','COMPLETED') AND consultation_start_time>=check_in_time) AS p90_wait_minutes
FROM cohort;
SELECT service_date,doctor_id,COUNT(*) AS checkins,
 COUNT(*) FILTER(WHERE status='COMPLETED') AS completed,
 COUNT(*) FILTER(WHERE status='NO_SHOW') AS no_shows
FROM queue_entry
WHERE service_date BETWEEN :'from_date'::date AND :'to_date'::date
 AND (:doctor_id=0 OR doctor_id=:doctor_id)
GROUP BY service_date,doctor_id ORDER BY service_date,doctor_id;
-- No rows is the expected active-duplicate result.
SELECT patient_id,doctor_id,service_date,COUNT(*) AS active_duplicates
FROM queue_entry WHERE status IN ('WAITING','IN_CONSULTATION')
GROUP BY patient_id,doctor_id,service_date HAVING COUNT(*)>1;
ROLLBACK;
