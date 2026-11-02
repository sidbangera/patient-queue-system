package com.patientqueue.system.service;
import java.time.LocalDate;
public interface QueueTokenAllocator {
    int nextToken(Long doctorId, LocalDate date);
}
