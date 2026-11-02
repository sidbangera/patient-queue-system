package com.patientqueue.system.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.sql.Date;

@Component
public class PostgresQueueTokenAllocator implements QueueTokenAllocator {
    private final JdbcTemplate jdbc;
    public PostgresQueueTokenAllocator(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Override public int nextToken(Long doctorId, LocalDate date) {
        // Database serializes conflicting UPSERTs on the (doctor_id, service_date) unique key.
        // This participates in QueueEntryService's @Transactional boundary.
        Integer result=jdbc.queryForObject("""
            INSERT INTO daily_queue_counter (doctor_id, service_date, last_token_number)
            VALUES (?, ?, 1)
            ON CONFLICT (doctor_id, service_date)
            DO UPDATE SET last_token_number = daily_queue_counter.last_token_number + 1
            RETURNING last_token_number
            """,Integer.class,doctorId,Date.valueOf(date));
        if(result == null) throw new IllegalStateException("Token allocation failed");
        return result;
    }
}
