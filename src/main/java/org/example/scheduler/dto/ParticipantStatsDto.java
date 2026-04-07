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

    // 대시보드 표시용 추가 필드
    private Long taskId;
    private int count;
    private LocalDate lastDate;

    public ParticipantStatsDto(String name, Map<Long, Integer> taskTotalCounts, Map<Long, LocalDate> taskLastAssignedDates) {
        this.name = name;
        this.taskTotalCounts = taskTotalCounts;
        this.taskLastAssignedDates = taskLastAssignedDates;
    }

    public ParticipantStatsDto(String name, int count, LocalDate lastDate) {
        this.name = name;
        this.count = count;
        this.lastDate = lastDate;
    }
}
