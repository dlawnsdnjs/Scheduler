package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.scheduler.domain.AssignmentStatus;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.ScheduleAssignment;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
        
        int C = participants.size();
        if (C == 0) return;

        // 1. 충돌 업무 ID 세트 구성 (양방향성 보장 및 효율적 필터링)
        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());
        taskRepository.findAll().stream()
                .filter(t -> t.getConflictingTasks().stream().anyMatch(ct -> ct.getId().equals(task.getId())))
                .forEach(t -> conflictIds.add(t.getId()));

        // 2. 해당 업무의 과거 배정 이력 로드 (간격 계산용)
        List<LocalDate> allOccurredDates = assignmentRepository.findByTaskId(task.getId()).stream()
                .map(ScheduleAssignment::getAssignedDate)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        for (LocalDate date : targetDates) {
            int needed = task.getRequiredParticipantsPerDay();
            int maxCount = participants.stream().mapToInt(p -> p.getTaskCount(task.getId())).max().orElse(0);

            // 3. 해당 날짜의 충돌 업무 배정 정보만 타게팅 조회 (최적화)
            List<ScheduleAssignment> conflictingAssignments = assignmentRepository.findByAssignedDateBetween(date, date).stream()
                    .filter(a -> conflictIds.contains(a.getTaskId()))
                    .toList();

            // 4. 후보군 필터링 및 점수 계산
            List<ParticipantScore> candidates = new ArrayList<>();
            for (Participant p : participants) {
                if (!p.isAvailable(date)) continue;
                
                // 충돌 체크
                if (conflictingAssignments.stream().anyMatch(a -> a.getParticipantId().equals(p.getId()))) continue;

                double score = p.calculateScore(task.getId(), date, allOccurredDates, C, maxCount);
                long gap = p.calculateGap(task.getId(), date, allOccurredDates, C);

                candidates.add(new ParticipantScore(p, score, gap));
            }

            candidates.sort(Comparator.comparingDouble(ParticipantScore::getScore).reversed());

            // 5. 최종 배정 및 이력 업데이트
            for (int i = 0; i < Math.min(needed, candidates.size()); i++) {
                ParticipantScore ps = candidates.get(i);
                ScheduleAssignment sa = new ScheduleAssignment(task.getId(), date, ps.p.getId());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                sa.setNote(String.format("점수:%.1f, 간격:%d회", ps.score, ps.gap));
                
                assignmentRepository.save(sa);
                ps.p.incrementTaskCount(task.getId(), date);
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

    private static class ParticipantScore {
        Participant p;
        double score;
        long gap;
        ParticipantScore(Participant p, double score, long gap) { this.p = p; this.score = score; this.gap = gap; }
        double getScore() { return score; }
    }
}
