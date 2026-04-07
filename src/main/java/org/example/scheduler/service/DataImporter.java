package org.example.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.*;
import org.example.scheduler.dto.ExportDataDto;
import org.example.scheduler.repository.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataImporter {
    private final ObjectMapper objectMapper;
    private final ParticipantRepository participantRepository;
    private final TaskDefinitionRepository taskRepository;
    private final ScheduleAssignmentRepository assignmentRepository;

    public void importData(String json) {
        try {
            ExportDataDto data = objectMapper.readValue(json, ExportDataDto.class);
            importParticipants(data.getParticipants());
            importTasks(data.getTasks());
            importTaskConflicts(data.getTasks());
            restoreParticipantStats(data.getParticipants());
            importAssignments(data.getAssignments());
        } catch (Exception e) {
            throw new RuntimeException("Import failed", e);
        }
    }

    private void importParticipants(List<ExportDataDto.ParticipantDataDto> dtos) {
        for (ExportDataDto.ParticipantDataDto pd : dtos) {
            Participant p = participantRepository.findByName(pd.getName()).orElse(new Participant(pd.getName(), pd.getJoinDate()));
            p.setJoinDate(pd.getJoinDate());
            p.setLeaveDate(pd.getLeaveDate());
            p.getUnavailableRanges().clear();
            if (pd.getUnavailableRanges() != null) pd.getUnavailableRanges().forEach(r -> p.addUnavailableRange(r.getStartDate(), r.getEndDate()));
            p.getAvailabilityRules().clear();
            if (pd.getAvailabilityRules() != null) pd.getAvailabilityRules().forEach(r -> p.addAvailabilityRule(r.getRuleType(), r.getRuleValue()));
            participantRepository.save(p);
        }
        participantRepository.flush();
    }

    private void importTasks(List<ExportDataDto.TaskDefinitionDataDto> dtos) {
        for (ExportDataDto.TaskDefinitionDataDto td : dtos) {
            TaskDefinition t = taskRepository.findByTaskName(td.getTaskName())
                    .orElse(new TaskDefinition(td.getTaskName(), td.getCycleType(), td.getCycleValue(), td.getRequiredParticipantsPerDay()));
            t.setCycleType(td.getCycleType());
            t.setCycleValue(td.getCycleValue());
            t.setRequiredParticipantsPerDay(td.getRequiredParticipantsPerDay());
            t.setColor(td.getColor());
            t.getAllowedParticipants().clear();
            if (td.getAllowedParticipantNames() != null) {
                for (String pName : td.getAllowedParticipantNames()) {
                    participantRepository.findByName(pName).ifPresent(p -> t.getAllowedParticipants().add(p));
                }
            }
            taskRepository.save(t);
        }
        taskRepository.flush();
    }

    private void importTaskConflicts(List<ExportDataDto.TaskDefinitionDataDto> dtos) {
        for (ExportDataDto.TaskDefinitionDataDto td : dtos) {
            taskRepository.findByTaskName(td.getTaskName()).ifPresent(t -> {
                t.getConflictingTasks().clear();
                if (td.getConflictingTaskNames() != null) {
                    for (String ctName : td.getConflictingTaskNames()) {
                        taskRepository.findByTaskName(ctName).ifPresent(ct -> t.addConflict(ct));
                    }
                }
                taskRepository.save(t);
            });
        }
        taskRepository.flush();
    }

    private void restoreParticipantStats(List<ExportDataDto.ParticipantDataDto> dtos) {
        Map<String, TaskDefinition> taskMap = taskRepository.findAll().stream().collect(Collectors.toMap(TaskDefinition::getTaskName, t -> t));
        for (ExportDataDto.ParticipantDataDto pd : dtos) {
            participantRepository.findByName(pd.getName()).ifPresent(p -> {
                Map<Long, Integer> counts = new HashMap<>();
                if (pd.getTaskTotalCounts() != null) {
                    pd.getTaskTotalCounts().forEach((tName, count) -> {
                        if (taskMap.containsKey(tName)) counts.put(taskMap.get(tName).getId(), count);
                    });
                }
                p.setTaskTotalCounts(counts);
                Map<Long, LocalDate> dates = new HashMap<>();
                if (pd.getTaskLastAssignedDates() != null) {
                    pd.getTaskLastAssignedDates().forEach((tName, date) -> {
                        if (taskMap.containsKey(tName)) dates.put(taskMap.get(tName).getId(), date);
                    });
                }
                p.setTaskLastAssignedDates(dates);
                participantRepository.save(p);
            });
        }
    }

    private void importAssignments(List<ExportDataDto.AssignmentDataDto> dtos) {
        Map<String, TaskDefinition> taskMap = taskRepository.findAll().stream().collect(Collectors.toMap(TaskDefinition::getTaskName, t -> t));
        for (ExportDataDto.AssignmentDataDto ad : dtos) {
            TaskDefinition t = taskMap.get(ad.getTaskName());
            Participant p = participantRepository.findByName(ad.getParticipantName()).orElse(null);
            if (t != null && p != null) {
                List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(t.getId(), ad.getAssignedDate(), ad.getAssignedDate());
                if (existing.stream().noneMatch(ex -> ex.getParticipantId().equals(p.getId()))) {
                    ScheduleAssignment sa = new ScheduleAssignment(t, ad.getAssignedDate(), p);
                    sa.setStatus(AssignmentStatus.valueOf(ad.getStatus()));
                    sa.setNote(ad.getNote());
                    assignmentRepository.save(sa);
                }
            }
        }
    }
}
