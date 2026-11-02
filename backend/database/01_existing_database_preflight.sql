-- READ ONLY. Run this before migration and inspect all results.
SELECT COUNT(*) AS total_rows, COUNT(*) FILTER (WHERE check_in_time IS NULL) AS missing_checkins FROM queue_entry;
SELECT doctor_id, check_in_time::date AS proposed_service_date, token_number, COUNT(*) AS duplicates
 FROM queue_entry GROUP BY doctor_id,check_in_time::date,token_number HAVING COUNT(*) > 1;
SELECT patient_id,doctor_id,check_in_time::date AS proposed_service_date,COUNT(*) AS active_duplicates
 FROM queue_entry WHERE status IN ('WAITING','IN_CONSULTATION')
 GROUP BY patient_id,doctor_id,check_in_time::date HAVING COUNT(*)>1;
SELECT doctor_id,check_in_time::date AS proposed_service_date, COUNT(*) AS concurrent_consultations
 FROM queue_entry WHERE status='IN_CONSULTATION'
 GROUP BY doctor_id,check_in_time::date HAVING COUNT(*)>1;
