package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
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

@Component
@RequiredArgsConstructor
public class DistributionEngine {

    private final ScheduleAssignmentRepository assignmentRepository;
    private final TaskDefinitionRepository taskRepository;

    public List<LocalDate> getTargetDates(TaskDefinition task, LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;

        if ("WEEKLY".equals(task.getCycleType())) {
            Set<DayOfWeek> targetDays = Arrays.stream(task.getCycleValue().split(","))
                    .map(String::trim)
                    .map(this::parseKoreanDay)
                    .collect(Collectors.toSet());

            while (!current.isAfter(end)) {
                if (targetDays.contains(current.getDayOfWeek())) dates.add(current);
                current = current.plusDays(1);
            }
        } else if ("INTERVAL".equals(task.getCycleType())) {
            int interval = Integer.parseInt(task.getCycleValue());
            while (!current.isAfter(end)) {
                dates.add(current);
                current = current.plusDays(interval);
            }
        }
        return dates;
    }

    /**
     * 간격 기반 점수 최적화 (Gap-based Scoring) 배정
     * 공식: S = C - |G - C|
     */
    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(task.getId(), date, date);
        int neededCount = task.getRequiredParticipantsPerDay() - existing.size();
        if (neededCount <= 0) return;

        List<ScheduleAssignment> allDayAssignments = assignmentRepository.findByAssignedDateBetween(date, date);
        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());
        
        // C: 배정 가능한 총 인원수
        int C = participants.size();

        // 1. 후보군 필터링 (가용성 및 충돌 체크)
        List<Participant> available = participants.stream()
                .filter(p -> p.isAvailable(date))
                .filter(p -> existing.stream().noneMatch(a -> a.getParticipantId().equals(p.getId())))
                .filter(p -> {
                    return allDayAssignments.stream()
                            .filter(a -> a.getParticipantId().equals(p.getId()))
                            .map(ScheduleAssignment::getTaskId)
                            .noneMatch(conflictIds::contains);
                })
                .collect(Collectors.toList());

        // 2. 점수 계산
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, String> notes = new HashMap<>();

        for (Participant p : available) {
            LocalDate lastDate = p.getLastDate(task.getId());
            double score;
            String note;

            if (lastDate == LocalDate.MIN) {
                score = C; // 처음 배정되는 인원은 최고점
                note = String.format("신규 배정 (점수: %.1f)", score);
            } else {
                long G = ChronoUnit.DAYS.between(lastDate, date);
                score = C - Math.abs(G - C);
                note = String.format("간격 %d일 (점수: %.1f)", G, score);
            }
            
            // 희소성 가중치 미세 조정 (가용일 적은 사람 우선)
            double scarcityBonus = 1.0 - (availableDaysCount.getOrDefault(p.getId(), C) / (double)C);
            score += scarcityBonus;

            scores.put(p.getId(), score);
            notes.put(p.getId(), note);
        }

        // 3. 점수 내림차순 정렬
        available.sort((p1, p2) -> Double.compare(scores.get(p2.getId()), scores.get(p1.getId())));

        // 4. 최종 배정
        for (int i = 0; i < Math.min(neededCount, available.size()); i++) {
            Participant selected = available.get(i);
            ScheduleAssignment assignment = new ScheduleAssignment(task.getId(), date, selected.getId());
            assignment.setStatus(AssignmentStatus.AUTOMATIC);
            assignment.setNote(notes.get(selected.getId()));
            
            if (shouldSave) {
                assignmentRepository.save(assignment);
                selected.incrementTaskCount(task.getId(), date);
            }
        }
    }

    private DayOfWeek parseKoreanDay(String day) {
        return switch (day) {
            case "월", "MON" -> DayOfWeek.MONDAY;
            case "화", "TUE" -> DayOfWeek.TUESDAY;
            case "수", "WED" -> DayOfWeek.WEDNESDAY;
            case "목", "THU" -> DayOfWeek.THURSDAY;
            case "금", "FRI" -> DayOfWeek.FRIDAY;
            case "토", "SAT" -> DayOfWeek.SATURDAY;
            case "일", "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("지원하지 않는 요일 형식: " + day);
        };
    }
}
