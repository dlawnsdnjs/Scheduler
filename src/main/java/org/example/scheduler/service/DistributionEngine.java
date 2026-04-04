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
     * 사이클 기반 점수 최적화 (DP 및 슬라이딩 윈도우 개념 적용)
     * 전체 기간 동안의 점수 합산이 최대가 되도록 순차적으로 최선의 선택을 함.
     */
    public void distributeOptimized(TaskDefinition task, List<LocalDate> targetDates, List<Participant> participants) {
        log.info("Starting Scored Distribution for task: {}", task.getTaskName());
        
        int C = participants.size();
        if (C == 0) return;

        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());
        
        // 날짜별 배정 정보를 담을 임시 맵 (메모리 내 충돌 체크용)
        Map<LocalDate, List<Long>> currentAssignments = new HashMap<>();
        
        // 타 업무 배정 정보 미리 로드
        LocalDate start = targetDates.get(0);
        LocalDate end = targetDates.get(targetDates.size() - 1);
        List<ScheduleAssignment> otherAssignments = assignmentRepository.findByAssignedDateBetween(start, end)
                .stream().filter(a -> !a.getTaskId().equals(task.getId())).collect(Collectors.toList());

        for (LocalDate date : targetDates) {
            int needed = task.getRequiredParticipantsPerDay();
            
            // 현재 시점의 참여자별 횟수 통계 추출 (보너스 점수 계산용)
            int maxCount = participants.stream()
                    .mapToInt(p -> p.getTaskCount(task.getId()))
                    .max().orElse(0);

            // 1. 후보군 필터링 및 점수 계산
            List<ParticipantScore> candidates = new ArrayList<>();
            for (Participant p : participants) {
                if (!p.isAvailable(date)) continue;
                
                // 충돌 체크
                boolean hasConflict = otherAssignments.stream()
                        .filter(a -> a.getAssignedDate().equals(date) && a.getParticipantId().equals(p.getId()))
                        .anyMatch(a -> conflictIds.contains(a.getTaskId()));
                if (hasConflict) continue;

                // (1) 간격 점수 (S_gap = C - |G - C|)
                LocalDate last = p.getLastDate(task.getId());
                long gap = (last == LocalDate.MIN) ? C : ChronoUnit.DAYS.between(last, date);
                double gapScore = C - Math.abs(gap - C);

                // (2) 참여 횟수 보너스 (S_balance = (MaxCount - MyCount) * C)
                // 참여 횟수 차이가 1이라도 나면 간격 점수의 최대치(C)만큼의 보너스를 주어 우선 배정 유도
                int myCount = p.getTaskCount(task.getId());
                double balanceBonus = (maxCount - myCount) * (double)C;

                double totalScore = gapScore + balanceBonus;
                candidates.add(new ParticipantScore(p, totalScore, gap));
            }

            // 2. 점수 높은 순 정렬
            candidates.sort(Comparator.comparingDouble(ParticipantScore::getScore).reversed());

            // 3. 최선의 인원 선택 및 저장
            for (int i = 0; i < Math.min(needed, candidates.size()); i++) {
                ParticipantScore ps = candidates.get(i);
                ScheduleAssignment sa = new ScheduleAssignment(task.getId(), date, ps.p.getId());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                sa.setNote(String.format("점수:%.1f, 간격:%d", ps.score, ps.gap));
                
                assignmentRepository.save(sa);
                ps.p.incrementTaskCount(task.getId(), date);
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
