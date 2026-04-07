package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.scheduler.domain.*;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributionEngine {

    private final ScheduleAssignmentRepository assignmentRepository;
    private final TaskDefinitionRepository taskRepository;

    public void distributeOptimized(TaskDefinition task, List<LocalDate> targetDates, List<Participant> participants) {
        log.info("Starting Scored Distribution for task: {}", task.getTaskName());
        
        if (participants.isEmpty()) return;

        // 1. 충돌 업무 ID 세트 구성 (양방향성 보장 및 효율적 필터링)
        List<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toList());

        // 2. 해당 업무의 과거 배정 이력 로드 (간격 계산용)
        List<LocalDate> allOccurredDates = assignmentRepository.findByTaskId(task.getId()).stream()
                .map(ScheduleAssignment::getAssignedDate)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        List<ScheduleAssignment> assignments = assignmentRepository.findByTaskIdInAndAssignedDateInAndParticipantIdIn(conflictIds,targetDates, participants);
        List<ScheduleAssignment> newAssignments = new ArrayList<>();
        for (LocalDate date : targetDates) {
            int needed = task.getRequiredParticipantsPerDay();
            int maxCount = participants.stream().mapToInt(p -> p.getTaskCount(task.getId())).max().orElse(0);

            List<ScheduleAssignment> conflictingAssignments = assignments.stream()
                    .filter(a -> a.getAssignedDate().equals(date))
                    .toList();

            List<Candidate> selectedCandidates = CandidateGroup.from(participants)
                    .filterAvailable(date)
                    .excludeConflicting(conflictingAssignments)
                    .scoreAll(task, date, allOccurredDates, maxCount)
                    .getTopCandidates(needed);

            for (Candidate cand : selectedCandidates) {
                ScheduleAssignment sa = new ScheduleAssignment(task, date, cand.getParticipant());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                sa.setNote(String.format("점수:%.1f, 간격:%d회", cand.getScore(), cand.getGap()));
                newAssignments.add(sa);
                cand.getParticipant().incrementTaskCount(task.getId(), date);
            }
            
            if (!allOccurredDates.contains(date)) {
                allOccurredDates.add(date);
                Collections.sort(allOccurredDates);
            }
        }
        assignmentRepository.saveAll(newAssignments);
    }

    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
        distributeOptimized(task, Collections.singletonList(date), participants);
    }
}
