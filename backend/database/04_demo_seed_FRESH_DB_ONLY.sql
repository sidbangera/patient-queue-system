-- Synthetic demo data, ONLY for a disposable fresh DB. Not idempotent.
-- Never run on existing records or with real patient data.
BEGIN;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM queue_entry) OR EXISTS (SELECT 1 FROM patient) OR EXISTS (SELECT 1 FROM doctor) THEN
    RAISE EXCEPTION 'Synthetic seed only runs on a fresh empty patient/doctor/queue database';
 END IF;
END $$;
INSERT INTO doctor(name,age,department) VALUES
('Dr. Demo Asha',39,'General OP'),('Dr. Demo Rohan',44,'Orthopaedics'),('Dr. Demo Mira',37,'ENT');
INSERT INTO patient(name,age,contact)
SELECT 'Demo Patient '||n, 18+(n*3)%57, 'DEMO-ONLY-'||lpad(n::text,3,'0')
FROM generate_series(1,18) n;
-- Each doctor/day has 4 historical visits. Today one WAITING and one IN_CONSULTATION.
WITH indexed_doctors AS (
 SELECT id,ROW_NUMBER() OVER(ORDER BY id) AS d FROM doctor
), indexed_patients AS (
 SELECT id, ROW_NUMBER() OVER(ORDER BY id) AS p FROM patient
), simulated AS (
 SELECT d.id AS doctor_id, (CURRENT_DATE - days.n)::date AS service_date,
        visit.n AS token_number, d.d AS doctor_rank,days.n AS day_offset
 FROM indexed_doctors d CROSS JOIN generate_series(0,6) days(n)
 CROSS JOIN generate_series(1,4) visit(n)
)
INSERT INTO queue_entry(doctor_id,patient_id,service_date,token_number,status,check_in_time,consultation_start_time,consultation_end_time)
SELECT s.doctor_id,p.id,s.service_date,s.token_number,
 CASE WHEN s.day_offset=0 AND s.token_number=4 THEN 'WAITING'
      WHEN s.day_offset=0 AND s.token_number=3 THEN 'IN_CONSULTATION'
      WHEN s.token_number=2 THEN 'NO_SHOW'
      ELSE 'COMPLETED' END,
 s.service_date + TIME '09:00' + (s.token_number * INTERVAL '35 minutes'),
 CASE WHEN s.token_number=2 OR (s.day_offset=0 AND s.token_number=4) THEN NULL
 ELSE s.service_date + TIME '09:00' + (s.token_number * INTERVAL '35 minutes') + (10+s.token_number*4) * INTERVAL '1 minute' END,
 CASE WHEN s.token_number=2 OR (s.day_offset=0 AND s.token_number IN (3,4)) THEN NULL
 ELSE s.service_date + TIME '09:00' + (s.token_number * INTERVAL '35 minutes') + (25+s.token_number*6) * INTERVAL '1 minute' END
FROM simulated s
JOIN indexed_patients p ON p.p=(((s.doctor_rank-1)*4 + s.token_number + s.day_offset*2-1)%18)+1;
INSERT INTO daily_queue_counter(doctor_id,service_date,last_token_number)
 SELECT doctor_id,service_date,MAX(token_number) FROM queue_entry GROUP BY doctor_id,service_date
 ON CONFLICT (doctor_id,service_date) DO UPDATE
 SET last_token_number=GREATEST(daily_queue_counter.last_token_number,EXCLUDED.last_token_number);
COMMIT;
