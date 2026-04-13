package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.*;
import org.example.scheduler.dto.CalendarAssignmentDto;
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
public class DistributionService {

    private final TaskDefinitionRepository taskRepository;
    private final ParticipantRepository participantRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final DistributionEngine distributionEngine;

    @Transactional
    public void distribute(Long taskId, int year, int month) {
        distribute(Collections.singletonList(taskId), year, month);
    }

    @Transactional
    public void distribute(List<Long> taskIds, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        distribute(taskIds, start, end);
    }

    @Transactional
    public void distribute(Long taskId, LocalDate start, LocalDate end) {
        distribute(Collections.singletonList(taskId), start, end);
    }

    @Transactional
    public void distribute(List<Long> taskIds, LocalDate start, LocalDate end) {
        if (taskIds == null || taskIds.isEmpty()) return;

        for (Long taskId : taskIds) {
            TaskDefinition task = taskRepository.findById(taskId).orElse(null);
            if (task == null) continue;

            List<ScheduleAssignment> toDelete = assignmentRepository.findByTaskIdAndAssignedDateBetweenAndStatus(taskId, start, end, AssignmentStatus.AUTOMATIC);
            List<Participant> allowedParticipants = task.getAllowedParticipants();

            if (allowedParticipants.isEmpty()) continue;

            // 1. 통계 원복
            for (Participant p : allowedParticipants) {
                long deletedCount = toDelete.stream().filter(a -> a.getParticipantId().equals(p.getId())).count();
                p.getTaskTotalCounts().put(taskId, Math.max(0, p.getTaskCount(taskId) - (int)deletedCount));

                LocalDate lastDate = assignmentRepository.findByTaskIdAndAssignedDateBefore(taskId, start).stream()
                        .filter(a -> a.getParticipantId().equals(p.getId()))
                        .map(ScheduleAssignment::getAssignedDate)
                        .max(LocalDate::compareTo)
                        .orElse(LocalDate.MIN);
                p.getTaskLastAssignedDates().put(taskId, lastDate);
                participantRepository.save(p);
            }
            assignmentRepository.deleteAll(toDelete);

            // 2. 배정 대상 날짜 생성
            List<LocalDate> targetDates = distributionEngine.getTargetDates(task, start, end);
            Collections.sort(targetDates);
            
            if (targetDates.isEmpty()) continue;

            // 3. 최적화 배정 수행
            distributionEngine.distributeOptimized(task, targetDates, allowedParticipants);
        }
    }

    @Transactional
    public void cancelAndReplace(Long assignmentId) {
        ScheduleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        
        Long taskId = assignment.getTaskId();
        LocalDate date = assignment.getAssignedDate();
        TaskDefinition task = assignment.getTask();
        Participant p = assignment.getParticipant();

        p.addUnavailableRange(date, date);
        p.getTaskTotalCounts().put(taskId, Math.max(0, p.getTaskCount(taskId) - 1));

        assignmentRepository.delete(assignment);

        List<Participant> allowedParticipants = task.getAllowedParticipants();
        distributionEngine.assignForDate(task, date, allowedParticipants, null, true);
    }

    @Transactional
    public void reassignAssignment(Long id) {
        cancelAndReplace(id);
    }

    @Transactional(readOnly = true)
    public List<CalendarAssignmentDto> getCalendarAssignments(int year, int month, Long filterTaskId, Long filterParticipantId) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<ScheduleAssignment> assignments = assignmentRepository.findByAssignedDateBetween(start, end);

        if (filterTaskId != null) {
            assignments = assignments.stream().filter(a -> a.getTaskId().equals(filterTaskId)).collect(Collectors.toList());
        }
        if (filterParticipantId != null) {
            assignments = assignments.stream().filter(a -> a.getParticipantId().equals(filterParticipantId)).collect(Collectors.toList());
        }

        Map<Long, TaskDefinition> tasks = taskRepository.findAllById(assignments.stream().map(ScheduleAssignment::getTaskId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(TaskDefinition::getId, t -> t));
        Map<Long, Participant> participants = participantRepository.findAllById(assignments.stream().map(ScheduleAssignment::getParticipantId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Participant::getId, p -> p));

        Map<LocalDate, List<CalendarAssignmentDto.AssignmentDetailDto>> grouped = assignments.stream()
                .filter(a -> tasks.get(a.getTaskId()) != null && participants.get(a.getParticipantId()) != null)
                .collect(Collectors.groupingBy(
                        ScheduleAssignment::getAssignedDate,
                        Collectors.mapping(a -> {
                            TaskDefinition t = tasks.get(a.getTaskId());
                            Participant p = participants.get(a.getParticipantId());
                            return new CalendarAssignmentDto.AssignmentDetailDto(
                                    a.getId(), a.getTaskId(), t.getTaskName(), t.getColor(), a.getParticipantId(), p.getName(), a.getStatus().name()
                            );
                        }, Collectors.toList())
                ));

        return grouped.entrySet().stream()
                .map(entry -> new CalendarAssignmentDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CalendarAssignmentDto::getDate))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAssignment(Long id) {
        assignmentRepository.deleteById(id);
    }

    @Transactional
    public void addManualAssignment(Long taskId, LocalDate date, Long participantId) {
        TaskDefinition task = taskRepository.findById(taskId).orElseThrow();
        Participant p = participantRepository.findById(participantId).orElseThrow();

        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, date, date);
        if (existing.stream().anyMatch(a -> a.getParticipantId().equals(participantId))) return;

        ScheduleAssignment assignment = new ScheduleAssignment(task, date, p);
        assignment.setStatus(AssignmentStatus.MANUAL_FIXED);
        assignmentRepository.save(assignment);

        p.incrementTaskCount(taskId, date);
    }

    @Transactional
    public void manualAssign(Long taskId, LocalDate date, Long participantId) {
        TaskDefinition task = taskRepository.findById(taskId).orElseThrow();
        Participant p = participantRepository.findById(participantId).orElseThrow();

        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, date, date);
        assignmentRepository.deleteAll(existing);

        ScheduleAssignment assignment = new ScheduleAssignment(task, date, p);
        assignment.setStatus(AssignmentStatus.MANUAL_FIXED);
        assignmentRepository.save(assignment);

        p.incrementTaskCount(taskId, date);
    }

    @Transactional
    public void swapAssignments(Long assignmentId1, Long assignmentId2) {
        ScheduleAssignment a1 = assignmentRepository.findById(assignmentId1).orElseThrow();
        ScheduleAssignment a2 = assignmentRepository.findById(assignmentId2).orElseThrow();

        Participant p1 = a1.getParticipant();
        Participant p2 = a2.getParticipant();

        if (!p1.isAvailable(a2.getAssignedDate()) || !p2.isAvailable(a1.getAssignedDate())) {
            throw new IllegalStateException("교체 대상 참여자가 해당 날짜에 가용하지 않습니다.");
        }

        a1.setParticipant(p2);
        a2.setParticipant(p1);

        a1.setStatus(AssignmentStatus.MANUAL_FIXED);
        a2.setStatus(AssignmentStatus.MANUAL_FIXED);
    }

    @Transactional
    public void clearAssignments(Long taskId, LocalDate start, LocalDate end) {
        List<ScheduleAssignment> toDelete = (taskId != null) ? 
                assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, start, end) :
                assignmentRepository.findByAssignedDateBetween(start, end);
        assignmentRepository.deleteAll(toDelete);
    }
}
