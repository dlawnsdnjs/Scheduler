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
     * 전체 일정에 대해 간격 기반 점수 최적화 방식으로 배분 수행
     */
    public void distributeWithScoring(TaskDefinition task, List<LocalDate> targetDates, List<Participant> allowedParticipants) {
        log.info("Starting Gap-based Scoring Distribution for task: {} (C={})", task.getTaskName(), allowedParticipants.size());
        
        // 1. 해당 월의 모든 배정 정보 사전 조회 (충돌 및 중복 체크용)
        LocalDate start = targetDates.isEmpty() ? LocalDate.now() : targetDates.get(0);
        LocalDate end = targetDates.isEmpty() ? LocalDate.now() : targetDates.get(targetDates.size() - 1);
        List<ScheduleAssignment> monthAssignments = assignmentRepository.findByAssignedDateBetween(start, end);
        
        // 2. 참여자별 마지막 배정일 초기화 (이미 DB에 있는 이전 배정일 포함)
        Map<Long, LocalDate> lastAssignedDates = new HashMap<>();
        for (Participant p : allowedParticipants) {
            lastAssignedDates.put(p.getId(), p.getLastDate(task.getId()));
        }

        // 3. 충돌 임무 ID 목록
        Set<Long> conflictIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());
        int C = allowedParticipants.size();

        // 4. 날짜별 순회 (점수 최적화를 위해 가급적 시계열 순으로 처리)
        for (LocalDate date : targetDates) {
            List<ScheduleAssignment> existing = monthAssignments.stream()
                    .filter(a -> a.getTaskId().equals(task.getId()) && a.getAssignedDate().equals(date))
                    .toList();
            
            int neededCount = task.getRequiredParticipantsPerDay() - existing.size();
            if (neededCount <= 0) continue;

            // 해당 날짜의 다른 업무 배정 정보 (충돌 체크용)
            List<ScheduleAssignment> otherDayAssignments = monthAssignments.stream()
                    .filter(a -> a.getAssignedDate().equals(date))
                    .toList();

            // 가용 참여자 필터링 및 점수 계산
            List<ScoringDetail> candidates = new ArrayList<>();
            for (Participant p : allowedParticipants) {
                // 불참 규칙 (최우선)
                if (!p.isAvailable(date)) continue;
                
                // 이미 해당 업무에 배정됨
                if (existing.stream().anyMatch(a -> a.getParticipantId().equals(p.getId()))) continue;
                
                // 충돌 임무 수행 중
                boolean hasConflict = otherDayAssignments.stream()
                        .filter(a -> a.getParticipantId().equals(p.getId()))
                        .map(ScheduleAssignment::getTaskId)
                        .anyMatch(conflictIds::contains);
                if (hasConflict) continue;

                // 점수 계산: S = C - |G - C|
                LocalDate lastDate = lastAssignedDates.getOrDefault(p.getId(), LocalDate.MIN);
                long gap;
                if (lastDate.equals(LocalDate.MIN)) {
                    gap = C; // 처음 배정인 경우 이상적인 간격 부여
                } else {
                    gap = ChronoUnit.DAYS.between(lastDate, date);
                }
                
                double score = C - Math.abs(gap - C);
                candidates.add(new ScoringDetail(p, score, gap));
            }

            // 점수순 정렬 (높은 점수 우선)
            candidates.sort(Comparator.comparingDouble(ScoringDetail::getScore).reversed());

            log.info("Date: {} - Candidates: {}", date, candidates.stream()
                    .limit(5)
                    .map(c -> String.format("%s(S:%.1f, G:%d)", c.participant.getName(), c.score, c.gap))
                    .collect(Collectors.joining(", ")));

            // 배정 수행
            for (int i = 0; i < Math.min(neededCount, candidates.size()); i++) {
                Participant selected = candidates.get(i).participant;
                ScheduleAssignment assignment = new ScheduleAssignment(task.getId(), date, selected.getId());
                assignment.setStatus(AssignmentStatus.AUTOMATIC);
                
                assignmentRepository.save(assignment);
                monthAssignments.add(assignment); // 메모리 내 목록 갱신
                
                // 참여자 통계 갱신
                selected.incrementTaskCount(task.getId(), date);
                lastAssignedDates.put(selected.getId(), date);
            }
        }
    }

    /**
     * 특정 날짜에 대한 단일 배정 (기존 로직 유지하되 점수 모델 적용)
     */
    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
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
            
            // 기존 가용 날짜 가중치 조금 섞기 (선택 사항)
            // score += 0.1 * availableDaysCount.getOrDefault(p.getId(), 0);

            candidates.add(new ScoringDetail(p, score, gap));
        }

        candidates.sort(Comparator.comparingDouble(ScoringDetail::getScore).reversed()
                .thenComparingInt(c -> c.participant.getTaskCount(task.getId())));

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
