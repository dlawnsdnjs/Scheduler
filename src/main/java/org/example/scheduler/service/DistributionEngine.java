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
        distributeOptimized(task, targetDates, participants, Collections.emptyList());
    }

    public void distributeOptimized(TaskDefinition task, List<LocalDate> targetDates, List<Participant> participants, List<ScheduleAssignment> providedAssignments) {
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

        // DB에서 충돌 업무 배정 정보 로드
        List<ScheduleAssignment> dbAssignments = assignmentRepository.findByTaskIdInAndAssignedDateInAndParticipantIdIn(conflictIds, targetDates, participants);
        
        // 메모리에 있는(방금 배정된) 정보와 합침
        List<ScheduleAssignment> allConflictingAssignments = new ArrayList<>(dbAssignments);
        allConflictingAssignments.addAll(providedAssignments.stream()
                .filter(a -> conflictIds.contains(a.getTask().getId()))
                .toList());

        List<ScheduleAssignment> newAssignments = new ArrayList<>();
        for (LocalDate date : targetDates) {
            int needed = task.getRequiredParticipantsPerDay();
            int maxCount = participants.stream().mapToInt(p -> p.getTaskCount(task.getId())).max().orElse(0);

            List<ScheduleAssignment> conflictingAssignmentsForDate = allConflictingAssignments.stream()
                    .filter(a -> a.getAssignedDate().equals(date))
                    .toList();

            List<Candidate> selectedCandidates = CandidateGroup.from(participants)
                    .filterAvailable(date)
                    .excludeConflicting(conflictingAssignmentsForDate)
                    .scoreAll(task, date, allOccurredDates, maxCount)
                    .getTopCandidates(needed);

            for (Candidate cand : selectedCandidates) {
                ScheduleAssignment sa = new ScheduleAssignment(task, date, cand.getParticipant());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                sa.setNote(String.format("점수:%.1f, 간격:%d회", cand.getScore(), cand.getGap()));
                newAssignments.add(sa);
                
                // 현재 배치 중인 결과에도 추가하여 같은 날 다음 업무 배정 시 참고하게 함
                allConflictingAssignments.add(sa);
                
                cand.getParticipant().incrementTaskCount(task.getId(), date);
            }
            
            if (!allOccurredDates.contains(date)) {
                allOccurredDates.add(date);
                Collections.sort(allOccurredDates);
            }
        }
        assignmentRepository.saveAll(newAssignments);
        
        // 새로 배정된 것들을 외부(Service)에서도 알 수 있게 collection에 추가할 수 있도록 return하거나 인자로 받은 list에 add할 수 있음.
        // 여기서는 Service에서 newAssignments를 모으도록 유도.
        if (providedAssignments instanceof ArrayList) {
            providedAssignments.addAll(newAssignments);
        }
    }

    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
        distributeOptimized(task, Collections.singletonList(date), participants);
    }
}
