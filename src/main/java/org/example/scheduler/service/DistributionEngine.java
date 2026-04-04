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

        // 해당 업무의 모든 과거 배정 날짜 로드 (간격 계산용)
        List<LocalDate> pastAssignmentDates = assignmentRepository.findByTaskId(task.getId()).stream()
                .map(ScheduleAssignment::getAssignedDate)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // 이번 배정 과정에서 확정되는 날짜들을 순차적으로 담을 리스트
        List<LocalDate> allOccurredDates = new ArrayList<>(pastAssignmentDates);

        for (LocalDate date : targetDates) {
            int needed = task.getRequiredParticipantsPerDay();
            
            // 현재 시점의 최다 참여 횟수 추출
            int maxCount = participants.stream()
                    .mapToInt(p -> p.getTaskCount(task.getId()))
                    .max().orElse(0);

            // 1. 후보군 필터링 및 점수 계산
            List<ParticipantScore> candidates = new ArrayList<>();
            for (Participant p : participants) {
                if (!p.isAvailable(date)) continue;
                
                boolean hasConflict = otherAssignments.stream()
                        .filter(a -> a.getAssignedDate().equals(date) && a.getParticipantId().equals(p.getId()))
                        .anyMatch(a -> conflictIds.contains(a.getTaskId()));
                if (hasConflict) continue;

                // [개선된 간격 계산] 마지막 배정 이후 업무가 몇 번 발생했는지 계산
                LocalDate last = p.getLastDate(task.getId());
                long G;
                if (last == LocalDate.MIN) {
                    G = C; // 신규 참여자는 한 사이클이 지난 것으로 간주
                } else {
                    // last 이후부터 현재 date 전까지 발생한 총 업무 횟수
                    final LocalDate lastFix = last;
                    G = allOccurredDates.stream()
                            .filter(d -> d.isAfter(lastFix) && d.isBefore(date))
                            .count() + 1; // 이번 차례 포함
                }

                double gapScore = C - Math.abs(G - C);
                int myCount = p.getTaskCount(task.getId());
                double balanceBonus = (maxCount - myCount) * (double)C;

                double totalScore = gapScore + balanceBonus;
                candidates.add(new ParticipantScore(p, totalScore, G));
            }

            // 2. 점수 높은 순 정렬
            candidates.sort(Comparator.comparingDouble(ParticipantScore::getScore).reversed());

            // 3. 최선의 인원 선택 및 저장
            for (int i = 0; i < Math.min(needed, candidates.size()); i++) {
                ParticipantScore ps = candidates.get(i);
                ScheduleAssignment sa = new ScheduleAssignment(task.getId(), date, ps.p.getId());
                sa.setStatus(AssignmentStatus.AUTOMATIC);
                sa.setNote(String.format("점수:%.1f, 발생간격:%d회", ps.score, ps.gap));
                
                assignmentRepository.save(sa);
                ps.p.incrementTaskCount(task.getId(), date);
            }
            
            // 이번 날짜를 발생 이력에 추가 (다음 날짜의 간격 계산에 반영)
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
