package org.example.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.*;
import org.example.scheduler.dto.ExportDataDto;
import org.example.scheduler.repository.ParticipantRepository;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final ParticipantRepository participantRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final TaskDefinitionRepository taskRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    @Transactional(readOnly = true)
    public String exportFullDataJson(LocalDate start, LocalDate end) {
        try {
            List<Participant> allParticipants = participantRepository.findAll();
            List<TaskDefinition> allTasks = taskRepository.findAll();
            Map<Long, String> taskIdToName = allTasks.stream()
                    .collect(Collectors.toMap(TaskDefinition::getId, TaskDefinition::getTaskName));

            // 1. 참여자 데이터 (통계 포함)
            List<ExportDataDto.ParticipantDataDto> pData = allParticipants.stream().map(p -> {
                Map<String, Integer> taskCounts = new HashMap<>();
                p.getTaskTotalCounts().forEach((id, count) -> {
                    if (taskIdToName.containsKey(id)) taskCounts.put(taskIdToName.get(id), count);
                });

                Map<String, LocalDate> lastDates = new HashMap<>();
                p.getTaskLastAssignedDates().forEach((id, date) -> {
                    if (taskIdToName.containsKey(id)) lastDates.put(taskIdToName.get(id), date);
                });

                return ExportDataDto.ParticipantDataDto.builder()
                        .name(p.getName())
                        .joinDate(p.getJoinDate())
                        .leaveDate(p.getLeaveDate())
                        .unavailableRanges(p.getUnavailableRanges().stream()
                                .map(r -> new ExportDataDto.UnavailableRangeDto(r.getStartDate(), r.getEndDate()))
                                .collect(Collectors.toList()))
                        .availabilityRules(p.getAvailabilityRules().stream()
                                .map(r -> new ExportDataDto.AvailabilityRuleDto(r.getRuleType(), r.getRuleValue()))
                                .collect(Collectors.toList()))
                        .taskTotalCounts(taskCounts)
                        .taskLastAssignedDates(lastDates)
                        .build();
            }).collect(Collectors.toList());

            // 2. 업무 정의 데이터
            List<ExportDataDto.TaskDefinitionDataDto> tData = allTasks.stream().map(t -> ExportDataDto.TaskDefinitionDataDto.builder()
                    .taskName(t.getTaskName())
                    .cycleType(t.getCycleType())
                    .cycleValue(t.getCycleValue())
                    .requiredParticipantsPerDay(t.getRequiredParticipantsPerDay())
                    .color(t.getColor())
                    .allowedParticipantNames(t.getAllowedParticipants().stream().map(Participant::getName).collect(Collectors.toList()))
                    .conflictingTaskNames(t.getConflictingTasks().stream().map(TaskDefinition::getTaskName).collect(Collectors.toList()))
                    .build()
            ).collect(Collectors.toList());

            // 3. 배정 일정 데이터
            List<ScheduleAssignment> assignments = (start != null && end != null) 
                    ? assignmentRepository.findByAssignedDateBetween(start, end)
                    : assignmentRepository.findAll();
            
            List<ExportDataDto.AssignmentDataDto> aData = assignments.stream().map(a -> {
                String taskName = taskIdToName.getOrDefault(a.getTaskId(), "Unknown");
                String participantName = allParticipants.stream()
                        .filter(p -> p.getId().equals(a.getParticipantId()))
                        .findFirst()
                        .map(Participant::getName)
                        .orElse("Unknown");
                return ExportDataDto.AssignmentDataDto.builder()
                        .taskName(taskName)
                        .assignedDate(a.getAssignedDate())
                        .participantName(participantName)
                        .status(a.getStatus().name())
                        .note(a.getNote())
                        .build();
            }).collect(Collectors.toList());

            ExportDataDto fullData = new ExportDataDto(pData, tData, aData);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fullData);
        } catch (Exception e) {
            throw new RuntimeException("데이터 내보내기 실패", e);
        }
    }

    @Transactional
    public void importFullDataJson(String json) {
        try {
            ExportDataDto data = objectMapper.readValue(json, ExportDataDto.class);

            // Step 1: 참여자 생성/업데이트
            for (ExportDataDto.ParticipantDataDto pd : data.getParticipants()) {
                Participant p = participantRepository.findByName(pd.getName()).orElse(new Participant(pd.getName(), pd.getJoinDate()));
                p.setJoinDate(pd.getJoinDate());
                p.setLeaveDate(pd.getLeaveDate());
                
                // 불참 기간/규칙 초기화 후 재설정
                p.getUnavailableRanges().clear();
                if (pd.getUnavailableRanges() != null) {
                    pd.getUnavailableRanges().forEach(r -> p.addUnavailableRange(r.getStartDate(), r.getEndDate()));
                }
                
                p.getAvailabilityRules().clear();
                if (pd.getAvailabilityRules() != null) {
                    pd.getAvailabilityRules().forEach(r -> p.addAvailabilityRule(r.getRuleType(), r.getRuleValue()));
                }
                
                participantRepository.save(p);
            }
            participantRepository.flush();

            // Step 2: 업무 생성 및 참여자 매핑
            for (ExportDataDto.TaskDefinitionDataDto td : data.getTasks()) {
                TaskDefinition t = taskRepository.findByTaskName(td.getTaskName())
                        .orElse(new TaskDefinition(td.getTaskName(), td.getCycleType(), td.getCycleValue(), td.getRequiredParticipantsPerDay()));
                
                t.setCycleType(td.getCycleType());
                t.setCycleValue(td.getCycleValue());
                t.setRequiredParticipantsPerDay(td.getRequiredParticipantsPerDay());
                t.setColor(td.getColor());
                
                // 참여자 매핑
                t.getAllowedParticipants().clear();
                if (td.getAllowedParticipantNames() != null) {
                    for (String pName : td.getAllowedParticipantNames()) {
                        participantRepository.findByName(pName).ifPresent(p -> t.getAllowedParticipants().add(p));
                    }
                }
                taskRepository.save(t);
            }
            taskRepository.flush();

            // Step 3: 업무 간 충돌 매핑
            for (ExportDataDto.TaskDefinitionDataDto td : data.getTasks()) {
                taskRepository.findByTaskName(td.getTaskName()).ifPresent(t -> {
                    t.getConflictingTasks().clear();
                    if (td.getConflictingTaskNames() != null) {
                        for (String ctName : td.getConflictingTaskNames()) {
                            taskRepository.findByTaskName(ctName).ifPresent(ct -> t.getConflictingTasks().add(ct));
                        }
                    }
                    taskRepository.save(t);
                });
            }
            taskRepository.flush();

            // 참여자 통계 복구 (Task ID가 필요하므로 업무 생성 후에 진행)
            Map<String, TaskDefinition> taskMap = taskRepository.findAll().stream()
                    .collect(Collectors.toMap(TaskDefinition::getTaskName, t -> t));
            
            for (ExportDataDto.ParticipantDataDto pd : data.getParticipants()) {
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

            // Step 4: 배정 정보 생성
            for (ExportDataDto.AssignmentDataDto ad : data.getAssignments()) {
                TaskDefinition t = taskMap.get(ad.getTaskName());
                Participant p = participantRepository.findByName(ad.getParticipantName()).orElse(null);
                
                if (t != null && p != null) {
                    // 중복 체크 (동일 날짜, 동일 업무, 동일 인원)
                    List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(t.getId(), ad.getAssignedDate(), ad.getAssignedDate());
                    if (existing.stream().noneMatch(ex -> ex.getParticipantId().equals(p.getId()))) {
                        ScheduleAssignment sa = new ScheduleAssignment(t.getId(), ad.getAssignedDate(), p.getId());
                        sa.setStatus(AssignmentStatus.valueOf(ad.getStatus()));
                        sa.setNote(ad.getNote());
                        assignmentRepository.save(sa);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("데이터 가져오기 실패", e);
        }
    }
}
