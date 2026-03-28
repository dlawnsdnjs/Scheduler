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

    public void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount, boolean shouldSave) {
        // 1. 현재 업무에 이미 배정된 정보
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(task.getId(), date, date);
        int neededCount = task.getRequiredParticipantsPerDay() - existing.size();
        if (neededCount <= 0) return;

        // 2. 해당 날짜의 모든 배정 정보 (다른 업무 포함)
        List<ScheduleAssignment> allDayAssignments = assignmentRepository.findByAssignedDateBetween(date, date);
        
        // 3. 충돌 관계 로드 (캐싱을 고려할 수 있으나 우선 정확성을 위해 매번 조회)
        Set<Long> myConflicts = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());
        List<TaskDefinition> allTasks = taskRepository.findAll();
        Set<Long> tasksThatConflictWithMe = allTasks.stream()
                .filter(t -> t.getConflictingTasks().stream().anyMatch(ct -> ct.getId().equals(task.getId())))
                .map(TaskDefinition::getId)
                .collect(Collectors.toSet());

        // 4. 가용 참여자 필터링
        List<Participant> available = participants.stream()
                .filter(p -> p.isAvailable(date))
                .filter(p -> existing.stream().noneMatch(a -> a.getParticipantId().equals(p.getId())))
                .filter(p -> {
                    // 당일 이 참여자가 배정된 다른 업무들 확인
                    List<Long> assignedIds = allDayAssignments.stream()
                            .filter(a -> a.getParticipantId().equals(p.getId()))
                            .map(ScheduleAssignment::getTaskId)
                            .toList();
                    
                    for (Long assignedId : assignedIds) {
                        if (assignedId.equals(task.getId())) continue;
                        // 양방향 충돌 체크
                        if (myConflicts.contains(assignedId) || tasksThatConflictWithMe.contains(assignedId)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        available.sort(Comparator.comparingInt((Participant p) -> p.getTaskCount(task.getId()))
                .thenComparingInt(p -> availableDaysCount.getOrDefault(p.getId(), 0))
                .thenComparing(p -> p.getLastDate(task.getId())));

        for (int i = 0; i < Math.min(neededCount, available.size()); i++) {
            Participant selected = available.get(i);
            ScheduleAssignment assignment = new ScheduleAssignment(task.getId(), date, selected.getId());
            assignment.setStatus(AssignmentStatus.AUTOMATIC);
            if (shouldSave) {
                assignmentRepository.save(assignment);
                // 실제 저장될 때만 참여자 통계 업데이트
                selected.incrementTaskCount(task.getId(), date);
            }
        }
    }

    private DayOfWeek parseKoreanDay(String day) {
        switch (day) {
            case "월": case "MON": return DayOfWeek.MONDAY;
            case "화": case "TUE": return DayOfWeek.TUESDAY;
            case "수": case "WED": return DayOfWeek.WEDNESDAY;
            case "목": case "THU": return DayOfWeek.THURSDAY;
            case "금": case "FRI": return DayOfWeek.FRIDAY;
            case "토": case "SAT": return DayOfWeek.SATURDAY;
            case "일": case "SUN": return DayOfWeek.SUNDAY;
            default: throw new IllegalArgumentException("지원하지 않는 요일 형식: " + day);
        }
    }
}
