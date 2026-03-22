package org.example.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarAssignmentDto {
    private LocalDate date;
    private List<AssignmentDetailDto> assignments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentDetailDto {
        private Long assignmentId;
        private Long taskId;
        private String taskName;
        private String taskColor; // 추가
        private Long participantId;
        private String participantName;
        private String status;
    }
}
