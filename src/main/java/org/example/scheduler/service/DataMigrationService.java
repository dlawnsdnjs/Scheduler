package org.example.scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.dto.ParticipantStatsDto;
import org.example.scheduler.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import org.example.scheduler.domain.*;
import org.example.scheduler.dto.ExportDataDto;
import org.example.scheduler.dto.ParticipantStatsDto;
import org.example.scheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final ParticipantRepository participantRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final TaskDefinitionRepository taskRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional(readOnly = true)
    public String exportFullDataJson(LocalDate start, LocalDate end) {
        try {
            // 1. 참여자 통계 데이터
            List<ParticipantStatsDto> pStats = participantRepository.findAll().stream()
                    .map(p -> new ParticipantStatsDto(p.getName(), p.getTaskTotalCounts(), p.getTaskLastAssignedDates()))
                    .collect(Collectors.toList());

            // 2. 배정 일정 데이터
            List<ScheduleAssignment> assignments = assignmentRepository.findByAssignedDateBetween(start, end);
            List<ExportDataDto.AssignmentDataDto> aData = assignments.stream().map(a -> {
                TaskDefinition task = taskRepository.findById(a.getTaskId()).orElse(null);
                Participant p = participantRepository.findById(a.getParticipantId()).orElse(null);
                return new ExportDataDto.AssignmentDataDto(
                        task != null ? task.getTaskName() : "Unknown",
                        a.getAssignedDate(),
                        p != null ? p.getName() : "Unknown",
                        a.getStatus().name()
                );
            }).collect(Collectors.toList());

            ExportDataDto fullData = new ExportDataDto(pStats, aData);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fullData);
        } catch (Exception e) {
            throw new RuntimeException("데이터 내보내기 실패", e);
        }
    }

    @Transactional
    public void importFullDataJson(String json) {
        try {
            ExportDataDto data = objectMapper.readValue(json, ExportDataDto.class);
            
            // 1. 참여자 통계 복구
            for (ParticipantStatsDto s : data.getParticipantStats()) {
                participantRepository.findAll().stream()
                        .filter(p -> p.getName().equals(s.getName()))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setTaskTotalCounts(s.getTaskTotalCounts());
                            p.setTaskLastAssignedDates(s.getTaskLastAssignedDates());
                            participantRepository.save(p);
                        });
            }

            // 2. 배정 일정 복구 (기존 일정을 삭제하지 않고 병합하거나 필요시 처리)
            for (ExportDataDto.AssignmentDataDto a : data.getAssignments()) {
                TaskDefinition task = taskRepository.findAll().stream().filter(t -> t.getTaskName().equals(a.getTaskName())).findFirst().orElse(null);
                Participant p = participantRepository.findAll().stream().filter(part -> part.getName().equals(a.getParticipantName())).findFirst().orElse(null);
                
                if (task != null && p != null) {
                    // 중복 체크 후 저장 (동일 날짜, 동일 업무, 동일 인원)
                    List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(task.getId(), a.getAssignedDate(), a.getAssignedDate());
                    if (existing.stream().noneMatch(ex -> ex.getParticipantId().equals(p.getId()))) {
                        ScheduleAssignment sa = new ScheduleAssignment(task.getId(), a.getAssignedDate(), p.getId());
                        sa.setStatus(AssignmentStatus.valueOf(a.getStatus()));
                        assignmentRepository.save(sa);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("데이터 가져오기 실패", e);
        }
    }
}
