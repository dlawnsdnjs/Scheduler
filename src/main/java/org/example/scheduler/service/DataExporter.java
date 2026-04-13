package org.example.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.*;
import org.example.scheduler.dto.ExportDataDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataExporter {
    private final ObjectMapper objectMapper;

    public String export(List<Participant> participants, List<TaskDefinition> tasks, List<ScheduleAssignment> assignments) {
        Map<Long, String> taskIdToName = tasks.stream().collect(Collectors.toMap(TaskDefinition::getId, TaskDefinition::getTaskName));
        
        List<ExportDataDto.ParticipantDataDto> pData = participants.stream().map(p -> {
            Map<String, Integer> taskCounts = new HashMap<>();
            p.getTaskTotalCounts().forEach((id, count) -> {
                if (taskIdToName.containsKey(id)) taskCounts.put(taskIdToName.get(id), count);
            });
            Map<String, LocalDate> lastDates = new HashMap<>();
            p.getTaskLastAssignedDates().forEach((id, date) -> {
                if (taskIdToName.containsKey(id)) lastDates.put(taskIdToName.get(id), date);
            });
            return ExportDataDto.ParticipantDataDto.builder()
                    .name(p.getName()).joinDate(p.getJoinDate()).leaveDate(p.getLeaveDate())
                    .unavailableRanges(p.getUnavailableRanges().stream().map(r -> new ExportDataDto.UnavailableRangeDto(r.getStartDate(), r.getEndDate())).toList())
                    .availabilityRules(p.getAvailabilityRules().stream().map(r -> new ExportDataDto.AvailabilityRuleDto(r.getRuleType(), r.getRuleValue())).toList())
                    .taskTotalCounts(taskCounts).taskLastAssignedDates(lastDates).build();
        }).toList();

        List<ExportDataDto.TaskDefinitionDataDto> tData = tasks.stream().map(t -> ExportDataDto.TaskDefinitionDataDto.builder()
                .taskName(t.getTaskName()).cycleType(t.getCycleType().name()).cycleValue(t.getCycleValue())
                .requiredParticipantsPerDay(t.getRequiredParticipantsPerDay()).color(t.getColor())
                .allowedParticipantNames(t.getAllowedParticipants().stream().map(Participant::getName).toList())
                .conflictingTaskNames(t.getConflictingTasks().stream().map(TaskDefinition::getTaskName).toList()).build()
        ).toList();

        List<ExportDataDto.AssignmentDataDto> aData = assignments.stream().map(a -> ExportDataDto.AssignmentDataDto.builder()
                .taskName(taskIdToName.getOrDefault(a.getTaskId(), "Unknown")).assignedDate(a.getAssignedDate())
                .participantName(a.getParticipant().getName()).status(a.getStatus().name()).note(a.getNote()).build()
        ).toList();

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new ExportDataDto(pData, tData, aData));
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
    }
}
