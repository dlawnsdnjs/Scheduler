package org.example.scheduler.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.example.scheduler.domain.AssignmentStatus;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.ScheduleAssignment;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
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

    private static final int MAX_ITERATIONS = 5000;
    private int iterationCount = 0;

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

    public void distributeWithScoring(TaskDefinition task, List<LocalDate> targetDates, List<Participant> allowedParticipants) {
        log.info("Starting Optimized Backtracking Distribution for task: {} (C={})", task.getTaskName(), allowedParticipants.size());
        iterationCount = 0;

        // 1. 초기 데이터 준비
        LocalDate start = targetDates.isEmpty() ? LocalDate.now() : targetDates.get(0);
        LocalDate end = targetDates.isEmpty() ? LocalDate.now() : targetDates.get(targetDates.size() - 1);
        List<ScheduleAssignment> otherAssignments = assignmentRepository.findByAssignedDateBetween(start, end)
                .stream().filter(a -> !a.getTaskId().equals(task.getId())).collect(Collectors.toList());

        Map<Long, LocalDate> initialLastDates = new HashMap<>();
        for (Participant p : allowedParticipants) {
            initialLastDates.put(p.getId(), p.getLastDate(task.getId()));
        }

        // 2. 백트래킹 탐색 수행
        List<ScheduleAssignment> result = new ArrayList<>();
        boolean success = backtrack(0, targetDates, task, allowedParticipants, otherAssignments, initialLastDates, result);

        if (success) {
            log.info("Optimization successful! Saving {} assignments.", result.size());
            for (ScheduleAssignment sa : result) {
                assignmentRepository.save(sa);
                // 통계 업데이트
                Participant p = allowedParticipants.stream().filter(part -> part.getId().equals(sa.getParticipantId())).findFirst().orElse(null);
                if (p != null) p.incrementTaskCount(task.getId(), sa.getAssignedDate());
            }
        } else {
            log.error("Failed to find a full assignment schedule that satisfies all constraints.");
            // 실패 시 최소한의 배정이라도 시도하거나 사용자에게 알림 (여기서는 로그만 남김)
        }
    }

    private boolean backtrack(int dateIdx, List<LocalDate> targetDates, TaskDefinition task, 
                             List<Participant> participants, List<ScheduleAssignment> otherAssignments,
                             Map<Long, LocalDate> lastDates, List<ScheduleAssignment> currentResult) {
        
        if (dateIdx >= targetDates.size()) return true; // 모든 날짜 배정 완료
        if (++iterationCount > MAX_ITERATIONS) return false;

        LocalDate date = targetDates.get(dateIdx);
        int needed = task.getRequiredParticipantsPerDay();
        int C = participants.size();
        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());

        // 1. 후보군 추출 및 점수 계산
        List<ScoringDetail> candidates = new ArrayList<>();
        for (Participant p : participants) {
            if (!p.isAvailable(date)) continue;
            
            boolean hasConflict = otherAssignments.stream()
                    .filter(a -> a.getAssignedDate().equals(date) && a.getParticipantId().equals(p.getId()))
                    .map(ScheduleAssignment::getTaskId)
                    .anyMatch(conflictIds::contains);
            if (hasConflict) continue;

            LocalDate lastDate = lastDates.getOrDefault(p.getId(), LocalDate.MIN);
            long gap = lastDate.equals(LocalDate.MIN) ? C : ChronoUnit.DAYS.between(lastDate, date);
            double score = C - Math.abs(gap - C);
            candidates.add(new ScoringDetail(p, score, gap));
        }

        if (candidates.size() < needed) return false; // 해당 날짜 충족 불가 -> 백트래킹

        // 2. 점수 높은 순으로 조합 시도
        candidates.sort(Comparator.comparingDouble(ScoringDetail::getScore).reversed());
        
        // 간단한 조합 생성 (needed가 작으므로 상위 N개 중 조합 선택)
        List<List<ScoringDetail>> combinations = getCombinations(candidates, needed);

        for (List<ScoringDetail> combo : combinations) {
            // 선택 적용
            List<ScheduleAssignment> tempAdded = new ArrayList<>();
            Map<Long, LocalDate> nextLastDates = new HashMap<>(lastDates);
            
            for (ScoringDetail sd : combo) {
                ScheduleAssignment sa = new ScheduleAssignment(task.getId(), date, sd.participant.getId());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                tempAdded.add(sa);
                nextLastDates.put(sd.participant.getId(), date);
            }

            currentResult.addAll(tempAdded);
            if (backtrack(dateIdx + 1, targetDates, task, participants, otherAssignments, nextLastDates, currentResult)) {
                return true;
            }
            // 실패 시 원복
            for (int i = 0; i < tempAdded.size(); i++) {
                currentResult.remove(currentResult.size() - 1);
            }
        }

        return false;
    }

    private List<List<ScoringDetail>> getCombinations(List<ScoringDetail> list, int n) {
        List<List<ScoringDetail>> results = new ArrayList<>();
        // 성능을 위해 후보가 너무 많으면 상위권 위주로 탐색 범위를 제한 (Pruning)
        int limit = Math.min(list.size(), 8); 
        generateCombo(new ArrayList<>(), list.subList(0, limit), n, 0, results);
        return results;
    }

    private void generateCombo(List<ScoringDetail> current, List<ScoringDetail> list, int n, int start, List<List<ScoringDetail>> results) {
        if (current.size() == n) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            generateCombo(current, list, n, i + 1, results);
            current.remove(current.size() - 1);
            if (results.size() >= 15) break; // 조합 시도 횟수 제한
        }
    }

    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
        // 단일 날짜 배정 로직도 점수 기반 유지 (백트래킹 없이 즉시 결정)
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(task.getId(), date, date);
        int neededCount = task.getRequiredParticipantsPerDay() - existing.size();
        if (neededCount <= 0) return;

        List<ScheduleAssignment> allDayAssignments = assignmentRepository.findByAssignedDateBetween(date, date);
        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());
        int C = participants.size();

        List<ScoringDetail> candidates = new ArrayList<>();
        for (Participant p : participants) {
            if (!p.isAvailable(date)) continue;
            if (existing.stream().anyMatch(a -> a.getParticipantId().equals(p.getId()))) continue;
            
            boolean hasConflict = allDayAssignments.stream()
                    .filter(a -> a.getParticipantId().equals(p.getId()))
                    .map(ScheduleAssignment::getTaskId)
                    .anyMatch(conflictIds::contains);
            if (hasConflict) continue;

            LocalDate lastDate = p.getLastDate(task.getId());
            long gap = lastDate.equals(LocalDate.MIN) ? C : ChronoUnit.DAYS.between(lastDate, date);
            double score = C - Math.abs(gap - C);
            candidates.add(new ScoringDetail(p, score, gap));
        }

        candidates.sort(Comparator.comparingDouble(ScoringDetail::getScore).reversed());

        for (int i = 0; i < Math.min(neededCount, candidates.size()); i++) {
            Participant selected = candidates.get(i).participant;
            ScheduleAssignment assignment = new ScheduleAssignment(task.getId(), date, selected.getId());
            assignment.setStatus(AssignmentStatus.AUTOMATIC);
            if (shouldSave) {
                assignmentRepository.save(assignment);
                selected.incrementTaskCount(task.getId(), date);
            }
        }
    }

    @Getter
    @ToString
    @RequiredArgsConstructor
    private static class ScoringDetail {
        private final Participant participant;
        private final double score;
        private final long gap;
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
