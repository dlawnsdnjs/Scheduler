package org.example.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantStatsDto {
    private String name;
    private Map<Long, Integer> taskTotalCounts;
    private Map<Long, LocalDate> taskLastAssignedDates;
}
