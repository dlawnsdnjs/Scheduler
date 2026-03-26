package org.example.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportDataDto {
    private List<ParticipantDataDto> participants;
    private List<TaskDefinitionDataDto> tasks;
    private List<AssignmentDataDto> assignments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantDataDto {
        private String name;
        private LocalDate joinDate;
        private LocalDate leaveDate;
        private List<UnavailableRangeDto> unavailableRanges;
        private List<AvailabilityRuleDto> availabilityRules;
        private Map<String, Integer> taskTotalCounts; // taskName -> count
        private Map<String, LocalDate> taskLastAssignedDates; // taskName -> lastDate
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UnavailableRangeDto {
        private LocalDate startDate;
        private LocalDate endDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AvailabilityRuleDto {
        private String ruleType;
        private String ruleValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaskDefinitionDataDto {
        private String taskName;
        private String cycleType;
        private String cycleValue;
        private int requiredParticipantsPerDay;
        private String color;
        private List<String> allowedParticipantNames;
        private List<String> conflictingTaskNames;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentDataDto {
        private String taskName;
        private LocalDate assignedDate;
        private String participantName;
        private String status;
        private String note;
    }
}
