package com.patientqueue.system.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class QueueCheckInDTO {
    @NotNull
    private Long patientId;
    @NotNull
    private Long doctorId;
}
