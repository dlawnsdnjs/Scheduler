package org.example.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportDataDto {
    private List<ParticipantStatsDto> participantStats;
    private List<AssignmentDataDto> assignments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentDataDto {
        private String taskName;
        private LocalDate assignedDate;
        private String participantName;
        private String status;
    }
}
