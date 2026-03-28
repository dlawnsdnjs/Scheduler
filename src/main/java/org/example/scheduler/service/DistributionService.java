package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.*;
import org.example.scheduler.dto.CalendarAssignmentDto;
import org.example.scheduler.repository.ParticipantRepository;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DistributionService {

    private final TaskDefinitionRepository taskRepository;
    private final ParticipantRepository participantRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final DistributionEngine distributionEngine;

    @Transactional
    public void distribute(Long taskId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        distribute(taskId, start, end);
    }

    @Transactional
    public void distribute(Long taskId, LocalDate start, LocalDate end) {
        TaskDefinition task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        List<ScheduleAssignment> toDelete = assignmentRepository.findByTaskIdAndAssignedDateBetweenAndStatus(taskId, start, end, AssignmentStatus.AUTOMATIC);
        List<Participant> allowedParticipants = task.getAllowedParticipants();
        int cycleSize = allowedParticipants.size();

        if (cycleSize == 0) return;

        // 1. 통계 원복
        for (Participant p : allowedParticipants) {
            long deletedCount = toDelete.stream().filter(a -> a.getParticipantId().equals(p.getId())).count();
            p.getTaskTotalCounts().put(taskId, Math.max(0, p.getTaskCount(taskId) - (int)deletedCount));

            LocalDate lastDate = assignmentRepository.findByTaskIdAndAssignedDateBefore(taskId, start).stream()
                    .filter(a -> a.getParticipantId().equals(p.getId()))
                    .map(ScheduleAssignment::getAssignedDate)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.MIN);
            p.getTaskLastAssignedDates().put(taskId, lastDate);
            participantRepository.save(p);
        }
        assignmentRepository.deleteAll(toDelete);

        // 2. 배정 대상 날짜 결정 (사이클 마무리 포함)
        List<LocalDate> targetDates = distributionEngine.getTargetDates(task, start, end);
        int totalSlots = targetDates.size() * task.getRequiredParticipantsPerDay();
        
        // 사이클을 완성하기 위해 필요한 추가 슬롯 계산
        int extraSlotsNeeded = (cycleSize - (totalSlots % cycleSize)) % cycleSize;
        
        List<LocalDate> allDates = new ArrayList<>(targetDates);
        if (extraSlotsNeeded > 0) {
            LocalDate searchStart = end.plusDays(1);
            while (extraSlotsNeeded > 0) {
                // 한 달씩 더해가며 필요한 만큼의 날짜 확보
                List<LocalDate> nextMonthDates = distributionEngine.getTargetDates(task, searchStart, searchStart.plusMonths(1));
                if (nextMonthDates.isEmpty()) break; // 무한루프 방지
                
                for (LocalDate d : nextMonthDates) {
                    if (extraSlotsNeeded <= 0) break;
                    allDates.add(d);
                    extraSlotsNeeded -= task.getRequiredParticipantsPerDay();
                }
                searchStart = searchStart.plusMonths(1);
            }
        }

        // 3. 참여자별 전체 가용 날짜 수 계산 (확장된 전체 기간 기준)
        Map<Long, Integer> availableDaysCount = calculateAvailableDays(allDates, allowedParticipants);

        // 4. 난이도 정렬 (확장된 전체 기간 기준)
        Map<LocalDate, Integer> dateDifficultyMap = new HashMap<>();
        for (LocalDate date : allDates) {
            long possibleCount = allowedParticipants.stream().filter(p -> p.isAvailable(date)).count();
            dateDifficultyMap.put(date, (int) possibleCount);
        }

        List<LocalDate> sortedDates = new ArrayList<>(allDates);
        sortedDates.sort(Comparator.comparingInt(dateDifficultyMap::get));

        // 5. 배정 수행
        for (LocalDate date : sortedDates) {
            // 요청된 기간(start~end) 내의 날짜만 DB에 저장
            boolean shouldSave = !date.isBefore(start) && !date.isAfter(end);
            distributionEngine.assignForDate(task, date, allowedParticipants, availableDaysCount, shouldSave);
        }
    }

    private Map<Long, Integer> calculateAvailableDays(List<LocalDate> targetDates, List<Participant> participants) {
        Map<Long, Integer> map = new HashMap<>();
        for (Participant p : participants) {
            long count = targetDates.stream().filter(p::isAvailable).count();
            map.put(p.getId(), (int) count);
        }
        return map;
    }

    @Transactional
    public void cancelAndReplace(Long assignmentId) {
        ScheduleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        
        Long taskId = assignment.getTaskId();
        LocalDate date = assignment.getAssignedDate();
        
        Participant p = participantRepository.findById(assignment.getParticipantId()).get();
        p.addUnavailableRange(date, date);
        p.getTaskTotalCounts().put(taskId, Math.max(0, p.getTaskCount(taskId) - 1));

        assignmentRepository.delete(assignment);

        TaskDefinition task = taskRepository.findById(taskId).get();
        List<Participant> allowedParticipants = task.getAllowedParticipants();
        
        // 당일 재배정을 위해 이번 달 가용성 재계산
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<LocalDate> monthTargetDates = distributionEngine.getTargetDates(task, start, end);
        Map<Long, Integer> availableDaysCount = calculateAvailableDays(monthTargetDates, allowedParticipants);

        distributionEngine.assignForDate(task, date, allowedParticipants, availableDaysCount, true);
    }

    @Transactional(readOnly = true)
    public List<CalendarAssignmentDto> getCalendarAssignments(int year, int month, Long filterTaskId, Long filterParticipantId) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<ScheduleAssignment> assignments = assignmentRepository.findByAssignedDateBetween(start, end);

        if (filterTaskId != null) {
            assignments = assignments.stream().filter(a -> a.getTaskId().equals(filterTaskId)).collect(Collectors.toList());
        }
        if (filterParticipantId != null) {
            assignments = assignments.stream().filter(a -> a.getParticipantId().equals(filterParticipantId)).collect(Collectors.toList());
        }

        Map<Long, TaskDefinition> tasks = taskRepository.findAllById(assignments.stream().map(ScheduleAssignment::getTaskId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(TaskDefinition::getId, t -> t));
        Map<Long, Participant> participants = participantRepository.findAllById(assignments.stream().map(ScheduleAssignment::getParticipantId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Participant::getId, p -> p));

        Map<LocalDate, List<CalendarAssignmentDto.AssignmentDetailDto>> grouped = assignments.stream()
                .filter(a -> {
                    TaskDefinition t = tasks.get(a.getTaskId());
                    Participant p = participants.get(a.getParticipantId());
                    return t != null && p != null; // 유효한 데이터만 필터링
                })
                .collect(Collectors.groupingBy(
                        ScheduleAssignment::getAssignedDate,
                        Collectors.mapping(a -> {
                            TaskDefinition t = tasks.get(a.getTaskId());
                            Participant p = participants.get(a.getParticipantId());
                            return new CalendarAssignmentDto.AssignmentDetailDto(
                                    a.getId(), a.getTaskId(), t.getTaskName(), t.getColor(), a.getParticipantId(), p.getName(), a.getStatus().name()
                            );
                        }, Collectors.toList())
                ));

        return grouped.entrySet().stream()
                .map(entry -> new CalendarAssignmentDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CalendarAssignmentDto::getDate))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAssignment(Long id) {
        assignmentRepository.deleteById(id);
    }

    @Transactional
    public void addManualAssignment(Long taskId, LocalDate date, Long participantId) {
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, date, date);
        boolean duplicate = existing.stream().anyMatch(a -> a.getParticipantId().equals(participantId));
        
        if (duplicate) {
            return;
        }

        ScheduleAssignment assignment = new ScheduleAssignment(taskId, date, participantId);
        assignment.setStatus(AssignmentStatus.MANUAL_FIXED);
        assignmentRepository.save(assignment);

        Participant p = participantRepository.findById(participantId).get();
        p.incrementTaskCount(taskId, date);
    }

    @Transactional
    public void manualAssign(Long taskId, LocalDate date, Long participantId) {
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, date, date);
        assignmentRepository.deleteAll(existing);

        ScheduleAssignment assignment = new ScheduleAssignment(taskId, date, participantId);
        assignment.setStatus(AssignmentStatus.MANUAL_FIXED);
        assignmentRepository.save(assignment);

        Participant p = participantRepository.findById(participantId).get();
        p.incrementTaskCount(taskId, date);
    }

    @Transactional
    public void swapAssignments(Long assignmentId1, Long assignmentId2) {
        ScheduleAssignment a1 = assignmentRepository.findById(assignmentId1).orElseThrow();
        ScheduleAssignment a2 = assignmentRepository.findById(assignmentId2).orElseThrow();

        Participant p1 = participantRepository.findById(a1.getParticipantId()).get();
        Participant p2 = participantRepository.findById(a2.getParticipantId()).get();

        if (!p1.isAvailable(a2.getAssignedDate()) || !p2.isAvailable(a1.getAssignedDate())) {
            throw new IllegalStateException("교체 대상 참여자가 해당 날짜에 가용하지 않습니다.");
        }

        Long tempPId = a1.getParticipantId();
        a1.setParticipantId(a2.getParticipantId());
        a2.setParticipantId(tempPId);

        a1.setStatus(AssignmentStatus.MANUAL_FIXED);
        a2.setStatus(AssignmentStatus.MANUAL_FIXED);
    }

    @Transactional
    public void clearAssignments(Long taskId, LocalDate start, LocalDate end) {
        if (taskId != null) {
            // 특정 업무만 삭제
            List<ScheduleAssignment> toDelete = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, start, end);
            assignmentRepository.deleteAll(toDelete);
        } else {
            // 전체 업무 삭제
            List<ScheduleAssignment> toDelete = assignmentRepository.findByAssignedDateBetween(start, end);
            assignmentRepository.deleteAll(toDelete);
        }
    }
}
