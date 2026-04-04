package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.scheduler.domain.AssignmentStatus;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.ScheduleAssignment;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributionEngine {

    private final ScheduleAssignmentRepository assignmentRepository;

    private static final int MAX_ITERATIONS = 10000;
    private int iterationCount = 0;
    private double bestTotalScore = -1.0;
    private List<ScheduleAssignment> bestAssignments = new ArrayList<>();

    public List<LocalDate> getTargetDates(TaskDefinition task, LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isTargetDate(task, current)) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }
        return dates;
    }

    private boolean isTargetDate(TaskDefinition task, LocalDate date) {
        if ("WEEKLY".equals(task.getCycleType())) {
            String[] days = task.getCycleValue().split(",");
            String currentDay = parseToKoreanDay(date.getDayOfWeek());
            return Arrays.stream(days).map(String::trim).anyMatch(d -> d.equals(currentDay) || d.equalsIgnoreCase(date.getDayOfWeek().name().substring(0, 3)));
        } else if ("INTERVAL".equals(task.getCycleType())) {
            // 단순화를 위해 시작일 기준 간격 체크 (실제 비즈니스 로직에 맞게 조정 가능)
            return true; 
        }
        return false;
    }

    private String parseToKoreanDay(java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    /**
     * 전체 최적화 배정 (Backtracking)
     */
    public void distributeOptimized(TaskDefinition task, List<LocalDate> targetDates, List<Participant> participants) {
        this.iterationCount = 0;
        this.bestTotalScore = -1.0;
        this.bestAssignments = new ArrayList<>();

        // 타 업무 배정 정보 (충돌 체크용)
        LocalDate start = targetDates.get(0);
        LocalDate end = targetDates.get(targetDates.size() - 1);
        List<ScheduleAssignment> allDayAssignments = assignmentRepository.findByAssignedDateBetween(start, end);
        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());

        // 초기 상태 (마지막 배정일)
        Map<Long, LocalDate> lastAssignedDates = new HashMap<>();
        for (Participant p : participants) {
            lastAssignedDates.put(p.getId(), p.getLastDate(task.getId()));
        }

        solve(0, 0.0, new ArrayList<>(), targetDates, task, participants, allDayAssignments, conflictIds, lastAssignedDates);

        if (!bestAssignments.isEmpty()) {
            for (ScheduleAssignment sa : bestAssignments) {
                assignmentRepository.save(sa);
                participants.stream().filter(p -> p.getId().equals(sa.getParticipantId())).findFirst()
                        .ifPresent(p -> p.incrementTaskCount(task.getId(), sa.getAssignedDate()));
            }
            log.info("Successfully distributed with total score: {}", bestTotalScore);
        }
    }

    private void solve(int dateIdx, double currentScore, List<ScheduleAssignment> currentPath,
                       List<LocalDate> targetDates, TaskDefinition task, List<Participant> participants,
                       List<ScheduleAssignment> allOtherAssignments, Set<Long> conflictIds,
                       Map<Long, LocalDate> lastDates) {

        if (dateIdx == targetDates.size()) {
            if (currentScore > bestTotalScore) {
                bestTotalScore = currentScore;
                bestAssignments = new ArrayList<>(currentPath);
            }
            return;
        }

        if (++iterationCount > MAX_ITERATIONS) return;

        LocalDate date = targetDates.get(dateIdx);
        int needed = task.getRequiredParticipantsPerDay();
        int C = participants.size();

        // 후보군 추출 및 점수 계산
        List<ParticipantScore> candidates = new ArrayList<>();
        for (Participant p : participants) {
            if (!p.isAvailable(date)) continue;
            
            // 충돌 체크
            boolean hasConflict = allOtherAssignments.stream()
                    .filter(a -> a.getAssignedDate().equals(date) && a.getParticipantId().equals(p.getId()))
                    .anyMatch(a -> conflictIds.contains(a.getTaskId()));
            if (hasConflict) continue;

            LocalDate last = lastDates.getOrDefault(p.getId(), LocalDate.MIN);
            long G = (last == LocalDate.MIN) ? C : ChronoUnit.DAYS.between(last, date);
            double S = C - Math.abs(G - C);
            candidates.add(new ParticipantScore(p, S, G));
        }

        if (candidates.size() < needed) return; // 불가능한 경로

        // 점수 높은 순으로 정렬하여 탐색 효율화
        candidates.sort(Comparator.comparingDouble(ParticipantScore::getScore).reversed());

        // 조합 시도 (필요 인원이 1명 이상일 수 있으므로 상위 후보군에서 조합 생성)
        List<List<ParticipantScore>> combos = getCombinations(candidates, needed);
        for (List<ParticipantScore> combo : combos) {
            double comboScore = combo.stream().mapToDouble(ParticipantScore::getScore).sum();
            
            // 상태 업데이트
            List<ScheduleAssignment> nextPath = new ArrayList<>(currentPath);
            Map<Long, LocalDate> nextLastDates = new HashMap<>(lastDates);
            for (ParticipantScore ps : combo) {
                ScheduleAssignment sa = new ScheduleAssignment(task.getId(), date, ps.p.getId());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                sa.setNote(String.format("간격:%d, 점수:%.1f", ps.gap, ps.score));
                nextPath.add(sa);
                nextLastDates.put(ps.p.getId(), date);
            }

            solve(dateIdx + 1, currentScore + comboScore, nextPath, targetDates, task, participants, allOtherAssignments, conflictIds, nextLastDates);
            
            if (iterationCount > MAX_ITERATIONS) break;
        }
    }

    private List<List<ParticipantScore>> getCombinations(List<ParticipantScore> list, int n) {
        List<List<ParticipantScore>> result = new ArrayList<>();
        // 탐색 범위를 상위 후보군으로 제한 (성능 최적화)
        int searchRange = Math.min(list.size(), n + 2); 
        generateCombinations(result, new ArrayList<>(), list.subList(0, searchRange), n, 0);
        return result;
    }

    private void generateCombinations(List<List<ParticipantScore>> result, List<ParticipantScore> temp, List<ParticipantScore> list, int n, int start) {
        if (temp.size() == n) {
            result.add(new ArrayList<>(temp));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            temp.add(list.get(i));
            generateCombinations(result, temp, list, n, i + 1);
            temp.remove(temp.size() - 1);
        }
    }

    private static class ParticipantScore {
        Participant p;
        double score;
        long gap;
        ParticipantScore(Participant p, double score, long gap) { this.p = p; this.score = score; this.gap = gap; }
        double getScore() { return score; }
    }

    // 단일 날짜 수동 추가/변경 시 사용되는 그리디 배정 (호환성 유지)
    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
        // 기존 로직과 동일하되 점수 기반 정렬 적용
        int needed = task.getRequiredParticipantsPerDay();
        int C = participants.size();
        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());
        List<ScheduleAssignment> allDayAssignments = assignmentRepository.findByAssignedDateBetween(date, date);

        List<ParticipantScore> candidates = new ArrayList<>();
        for (Participant p : participants) {
            if (!p.isAvailable(date)) continue;
            boolean hasConflict = allDayAssignments.stream()
                    .filter(a -> a.getAssignedDate().equals(date) && a.getParticipantId().equals(p.getId()))
                    .anyMatch(a -> conflictIds.contains(a.getTaskId()));
            if (hasConflict) continue;

            LocalDate last = p.getLastDate(task.getId());
            long G = (last == LocalDate.MIN) ? C : ChronoUnit.DAYS.between(last, date);
            double S = C - Math.abs(G - C);
            candidates.add(new ParticipantScore(p, S, G));
        }

        candidates.sort(Comparator.comparingDouble(ParticipantScore::getScore).reversed());

        for (int i = 0; i < Math.min(needed, candidates.size()); i++) {
            ParticipantScore ps = candidates.get(i);
            ScheduleAssignment sa = new ScheduleAssignment(task.getId(), date, ps.p.getId());
            sa.setStatus(AssignmentStatus.AUTOMATIC);
            sa.setNote(String.format("간격:%d, 점수:%.1f", ps.gap, ps.score));
            if (shouldSave) {
                assignmentRepository.save(sa);
                ps.p.incrementTaskCount(task.getId(), date);
            }
        }
    }
}
