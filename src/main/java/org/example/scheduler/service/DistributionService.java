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
    public void distribute(List<Long> taskIds, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        distribute(taskIds, start, end);
    }

    @Transactional
    public void distribute(Long taskId, int year, int month) {
        distribute(Collections.singletonList(taskId), year, month);
    }

    @Transactional
    public void distribute(Long taskId, LocalDate start, LocalDate end) {
        distribute(Collections.singletonList(taskId), start, end);
    }

    @Transactional
    public void distribute(List<Long> taskIds, LocalDate start, LocalDate end) {
        if (taskIds == null || taskIds.isEmpty()) return;

        List<TaskDefinition> tasks = taskRepository.findAllById(taskIds);
        List<ScheduleAssignment> toDelete = assignmentRepository.findByTaskIdInAndAssignedDateBetweenAndStatus(taskIds, start, end, AssignmentStatus.AUTOMATIC);
        
        // 1. 배정 삭제 및 통계 동기화
        deleteAssignmentsAndSyncStats(toDelete);

        for (TaskDefinition task : tasks) {
            List<Participant> allowedParticipants = task.getAllowedParticipants();
            if (allowedParticipants.isEmpty()) continue;
            
            List<LocalDate> targetDates = task.getTargetDates(start, end);
            Collections.sort(targetDates);
            
            if (targetDates.isEmpty()) continue;

            distributionEngine.distributeOptimized(task, targetDates, allowedParticipants);
        }
    }

    private void deleteAssignmentsAndSyncStats(List<ScheduleAssignment> toDelete) {
        if (toDelete.isEmpty()) return;
        
        // 영향을 받는 (참여자, 업무) 쌍 추출
        Set<TaskParticipantPair> pairs = toDelete.stream()
                .map(a -> new TaskParticipantPair(a.getTask().getId(), a.getParticipant()))
                .collect(Collectors.toSet());

        assignmentRepository.deleteAll(toDelete);

        // 삭제 후 통계 재계산
        for (TaskParticipantPair pair : pairs) {
            syncParticipantStats(pair.participant, pair.taskId);
        }
    }

    private void syncParticipantStats(Participant p, Long taskId) {
        List<ScheduleAssignment> remaining = assignmentRepository.findByParticipantIdAndTaskId(p.getId(), taskId);
        int count = remaining.size();
        LocalDate lastDate = remaining.stream()
                .map(ScheduleAssignment::getAssignedDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.MIN);
        
        p.getTaskTotalCounts().put(taskId, count);
        p.getTaskLastAssignedDates().put(taskId, lastDate);
        participantRepository.save(p);
    }

    private static class TaskParticipantPair {
        Long taskId;
        Participant participant;

        TaskParticipantPair(Long taskId, Participant participant) {
            this.taskId = taskId;
            this.participant = participant;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TaskParticipantPair that = (TaskParticipantPair) o;
            return Objects.equals(taskId, that.taskId) && Objects.equals(participant.getId(), that.participant.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, participant.getId());
        }
    }

    private Map<Long, Integer> calculateAvailableDays(List<LocalDate> targetDates, List<Participant> participants) {
        Map<Long, Integer> map = new HashMap<>();
        for (Participant p : participants) {
            long count = targetDates.stream().filter(p::isAvailable).count();
            map.put(p.getId(), (int) count);
        }
        return map;
    }

    @Transactional
    public void reassignAssignment(Long assignmentId) {
        ScheduleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        
        // 1. 기존 배정 취소
        cancelAssignment(assignment);

        // 2. 새로운 배정 시도
        TaskDefinition task = assignment.getTask();
        List<Participant> allowedParticipants = task.getAllowedParticipants();
        
        LocalDate start = assignment.getAssignedDate().withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        
        List<LocalDate> targetDates = task.getTargetDates(start, end);
        Map<Long, Integer> availableDaysCount = calculateAvailableDays(targetDates, allowedParticipants);

        distributionEngine.assignForDate(task, assignment.getAssignedDate(), allowedParticipants, availableDaysCount, true);
    }

    private void cancelAssignment(ScheduleAssignment assignment) {
        Participant p = assignment.getParticipant();
        Long taskId = assignment.getTask().getId();
        p.cancelAssignment(taskId, assignment.getAssignedDate());
        assignmentRepository.delete(assignment);
        syncParticipantStats(p, taskId);
    }

    @Transactional(readOnly = true)
    public List<CalendarAssignmentDto> getCalendarAssignments(int year, int month, Long filterTaskId, Long filterParticipantId) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<ScheduleAssignment> assignments = assignmentRepository.findAllWithTaskAndParticipant(start, end);

        if (filterTaskId != null) {
            assignments = assignments.stream().filter(a -> a.getTaskId().equals(filterTaskId)).collect(Collectors.toList());
        }
        if (filterParticipantId != null) {
            assignments = assignments.stream().filter(a -> a.getParticipantId().equals(filterParticipantId)).collect(Collectors.toList());
        }

        Map<LocalDate, List<CalendarAssignmentDto.AssignmentDetailDto>> grouped = assignments.stream()
                .collect(Collectors.groupingBy(
                        ScheduleAssignment::getAssignedDate,
                        Collectors.mapping(a -> new CalendarAssignmentDto.AssignmentDetailDto(
                                    a.getId(), a.getTask().getId(), a.getTask().getTaskName(), a.getTask().getColor(), a.getParticipant().getId(), a.getParticipant().getName(), a.getStatus().name()
                            ), Collectors.toList())
                ));

        return grouped.entrySet().stream()
                .map(entry -> new CalendarAssignmentDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CalendarAssignmentDto::getDate))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAssignment(Long id) {
        assignmentRepository.findById(id).ifPresent(a -> {
            Participant p = a.getParticipant();
            Long taskId = a.getTask().getId();
            assignmentRepository.delete(a);
            syncParticipantStats(p, taskId);
        });
    }

    @Transactional
    public void addManualAssignment(Long taskId, LocalDate date, Long participantId) {
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, date, date);
        if (existing.stream().anyMatch(a -> a.getParticipantId().equals(participantId))) return;

        TaskDefinition task = taskRepository.findById(taskId).orElseThrow();
        Participant p = participantRepository.findById(participantId).orElseThrow();

        ScheduleAssignment assignment = new ScheduleAssignment(task, date, p);
        assignment.setStatus(AssignmentStatus.MANUAL_FIXED);
        assignmentRepository.save(assignment);

        p.incrementTaskCount(taskId, date);
    }

    @Transactional
    public void manualAssign(Long taskId, LocalDate date, Long participantId) {
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, date, date);
        deleteAssignmentsAndSyncStats(existing);

        TaskDefinition task = taskRepository.findById(taskId).orElseThrow();
        Participant p = participantRepository.findById(participantId).orElseThrow();

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

        Participant tempP = a1.getParticipant();
        a1.setParticipant(a2.getParticipant());
        a2.setParticipant(tempP);

        a1.setStatus(AssignmentStatus.MANUAL_FIXED);
        a2.setStatus(AssignmentStatus.MANUAL_FIXED);
        
        // Swap 후 통계 동기화 (마지막 날짜가 바뀔 수 있음)
        syncParticipantStats(p1, a1.getTask().getId());
        syncParticipantStats(p2, a2.getTask().getId());
    }

    @Transactional
    public void clearAssignments(Long taskId, LocalDate start, LocalDate end) {
        List<ScheduleAssignment> toDelete = (taskId != null) ? 
                assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, start, end) :
                assignmentRepository.findByAssignedDateBetween(start, end);
        deleteAssignmentsAndSyncStats(toDelete);
    }
}
