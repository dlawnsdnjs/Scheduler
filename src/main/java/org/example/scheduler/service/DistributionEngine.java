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
        for (LocalDate date : targetDates) {
            int needed = task.getRequiredParticipantsPerDay();
            int maxCount = participants.stream().mapToInt(p -> p.getTaskCount(task.getId())).max().orElse(0);

            // 3. 해당 날짜의 충돌 업무 배정 정보 조회
            List<ScheduleAssignment> conflictingAssignments = assignments.stream()
                    .filter(a -> a.getAssignedDate() == date)
                    .toList();

            // 4. 일급 컬렉션(CandidateGroup)을 활용한 후보군 선출
            List<Candidate> selectedCandidates = CandidateGroup.from(participants)
                    .filterAvailable(date)
                    .excludeConflicting(conflictingAssignments)
                    .scoreAll(task, date, allOccurredDates, maxCount)
                    .getTopCandidates(needed);

            // 5. 최종 배정 및 이력 업데이트
            for (Candidate cand : selectedCandidates) {
                ScheduleAssignment sa = new ScheduleAssignment(task, date, cand.getParticipant());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                sa.setNote(String.format("점수:%.1f, 간격:%d회", cand.getScore(), cand.getGap()));
                
                assignmentRepository.save(sa);
                cand.getParticipant().incrementTaskCount(task.getId(), date);
            }
            
            if (!allOccurredDates.contains(date)) {
                allOccurredDates.add(date);
                Collections.sort(allOccurredDates);
            }
        }
    }

    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
        distributeOptimized(task, Collections.singletonList(date), participants);
    }
}
