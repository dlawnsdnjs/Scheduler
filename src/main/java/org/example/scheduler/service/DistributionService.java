package org.example.scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.*;
import org.example.scheduler.dto.CalendarAssignmentDto;
import org.example.scheduler.dto.ParticipantStatsDto;
import org.example.scheduler.repository.ParticipantRepository;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DistributionService {

    private final TaskDefinitionRepository taskRepository;
    private final ParticipantRepository participantRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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

        // 1. 기존 자동 배정 일정 삭제 (지정된 범위 내의 자동 배정만 삭제)
        assignmentRepository.deleteByTaskIdAndAssignedDateBetweenAndStatus(taskId, start, end, AssignmentStatus.AUTOMATIC);

        // 2. 해당 기간의 모든 수행일 리스트 생성
        List<LocalDate> targetDates = getTargetDates(task, start, end);

        // 3. 해당 업무에 참여 가능한 참여자 목록 조회 및 가용 날짜 수 계산
        List<Participant> allowedParticipants = task.getAllowedParticipants();
        Map<Long, Integer> availableDaysCount = new HashMap<>();
        for (Participant p : allowedParticipants) {
            long count = targetDates.stream().filter(p::isAvailable).count();
            availableDaysCount.put(p.getId(), (int) count);
        }

        // 4. 날짜별 배정 수행
        for (LocalDate date : targetDates) {
            assignForDate(task, date, allowedParticipants, availableDaysCount);
        }
    }

    private List<LocalDate> getTargetDates(TaskDefinition task, LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;

        if ("WEEKLY".equals(task.getCycleType())) {
            Set<DayOfWeek> targetDays = Arrays.stream(task.getCycleValue().split(","))
                    .map(String::trim)
                    .map(day -> {
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
                    })
                    .collect(Collectors.toSet());

            while (!current.isAfter(end)) {
                if (targetDays.contains(current.getDayOfWeek())) {
                    dates.add(current);
                }
                current = current.plusDays(1);
            }
        } else if ("INTERVAL".equals(task.getCycleType())) {
            // cycleValue가 "2" (2일마다) 형태라고 가정
            int interval = Integer.parseInt(task.getCycleValue());
            while (!current.isAfter(end)) {
                dates.add(current);
                current = current.plusDays(interval);
            }
        }
        return dates;
    }

    @Transactional
    public void deleteAssignment(Long id) {
        assignmentRepository.deleteById(id);
    }

    @Transactional
    public void cancelAndReplace(Long assignmentId) {
        ScheduleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        
        Long taskId = assignment.getTaskId();
        LocalDate date = assignment.getAssignedDate();
        Long participantId = assignment.getParticipantId();

        // 1. 해당 참여자에게 해당 날짜 불참 등록 (UnavailableRange 추가)
        Participant p = participantRepository.findById(participantId).get();
        p.addUnavailableRange(date, date);
        
        // 2. 참여 횟수 원복 (취소되었으므로)
        p.getTaskTotalCounts().put(taskId, Math.max(0, p.getTaskCount(taskId) - 1));
        participantRepository.save(p);

        // 3. 기존 배정 삭제
        assignmentRepository.delete(assignment);

        // 4. 즉시 재배정 시도
        TaskDefinition task = taskRepository.findById(taskId).get();
        List<Participant> allowedParticipants = task.getAllowedParticipants();
        Map<Long, Integer> availableDaysCount = new HashMap<>();
        for (Participant ap : allowedParticipants) {
            LocalDate start = date.withDayOfMonth(1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            long count = getTargetDates(task, start, end).stream().filter(ap::isAvailable).count();
            availableDaysCount.put(ap.getId(), (int) count);
        }

        assignForDate(task, date, allowedParticipants, availableDaysCount);
    }

    @Transactional(readOnly = true)
    public String exportStatsJson() {
        try {
            List<Participant> participants = participantRepository.findAll();
            List<ParticipantStatsDto> stats = participants.stream()
                    .map(p -> new ParticipantStatsDto(p.getName(), p.getTaskTotalCounts(), p.getTaskLastAssignedDates()))
                    .collect(Collectors.toList());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(stats);
        } catch (Exception e) {
            throw new RuntimeException("데이터 내보내기 실패", e);
        }
    }

    @Transactional
    public void importStatsJson(String json) {
        try {
            List<ParticipantStatsDto> stats = objectMapper.readValue(json, new TypeReference<List<ParticipantStatsDto>>() {});
            for (ParticipantStatsDto s : stats) {
                // 이름으로 참여자 찾기 (또는 ID 매칭 로직)
                participantRepository.findAll().stream()
                        .filter(p -> p.getName().equals(s.getName()))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setTaskTotalCounts(s.getTaskTotalCounts());
                            p.setTaskLastAssignedDates(s.getTaskLastAssignedDates());
                            participantRepository.save(p);
                        });
            }
        } catch (Exception e) {
            throw new RuntimeException("데이터 가져오기 실패", e);
        }
    }

    private void assignForDate(TaskDefinition task, LocalDate date, List<Participant> participants, Map<Long, Integer> availableDaysCount) {
        // 이미 수동으로 배정된 인원이 있는지 확인
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(task.getId(), date, date);
        int alreadyAssignedCount = existing.size();
        int neededCount = task.getRequiredParticipantsPerDay() - alreadyAssignedCount;

        if (neededCount <= 0) return;

        // 해당 날짜의 모든 배정 정보 조회 (충돌 체크용)
        List<ScheduleAssignment> allDayAssignments = assignmentRepository.findByAssignedDateBetween(date, date);
        Set<Long> conflictingTaskIds = task.getConflictingTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());

        // 가용 참여자 필터링
        List<Participant> available = participants.stream()
                .filter(p -> p.isAvailable(date))
                // 1. 현재 업무에 이미 배정된 사람 제외
                .filter(p -> existing.stream().noneMatch(a -> a.getParticipantId().equals(p.getId())))
                // 2. 해당 날짜에 이미 배정된 업무 중 충돌 업무가 있는지 확인 (중복 배정 방지)
                .filter(p -> allDayAssignments.stream()
                        .filter(a -> a.getParticipantId().equals(p.getId()))
                        .noneMatch(a -> conflictingTaskIds.contains(a.getTaskId())))
                .collect(Collectors.toList());

        // [고도화된 정렬] 
        // 1순위: 해당 업무 누적 참여 횟수 적은 순
        // 2순위: 가용성이 제한적인 사람 우선 (이번 달 가용 날짜 수가 적은 사람)
        // 3순위: 마지막 배정일이 오래된 순
        available.sort(Comparator.comparingInt((Participant p) -> p.getTaskCount(task.getId()))
                .thenComparingInt(p -> availableDaysCount.getOrDefault(p.getId(), 0))
                .thenComparing(p -> p.getLastDate(task.getId())));

        // 배정 수행
        for (int i = 0; i < Math.min(neededCount, available.size()); i++) {
            Participant selected = available.get(i);
            ScheduleAssignment assignment = new ScheduleAssignment(task.getId(), date, selected.getId());
            assignment.setStatus(AssignmentStatus.AUTOMATIC);
            assignmentRepository.save(assignment);
            selected.incrementTaskCount(task.getId(), date);
        }
    }

    @Transactional(readOnly = true)
    public List<CalendarAssignmentDto> getCalendarAssignments(int year, int month, Long filterTaskId, Long filterParticipantId) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<ScheduleAssignment> assignments = assignmentRepository.findByAssignedDateBetween(start, end);

        // 필터링 적용
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
    public void manualAssign(Long taskId, LocalDate date, Long participantId) {
        // 기존의 해당 날짜/업무 배정 삭제 (수동 고정이라도 덮어쓰기)
        List<ScheduleAssignment> existing = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, date, date);
        assignmentRepository.deleteAll(existing);

        ScheduleAssignment assignment = new ScheduleAssignment(taskId, date, participantId);
        assignment.setStatus(AssignmentStatus.MANUAL_FIXED);
        assignmentRepository.save(assignment);

        // 참여자 횟수 갱신
        Participant p = participantRepository.findById(participantId).get();
        p.incrementTaskCount(taskId, date);
    }

    @Transactional
    public void swapAssignments(Long assignmentId1, Long assignmentId2) {
        ScheduleAssignment a1 = assignmentRepository.findById(assignmentId1)
                .orElseThrow(() -> new IllegalArgumentException("Assignment 1 not found"));
        ScheduleAssignment a2 = assignmentRepository.findById(assignmentId2)
                .orElseThrow(() -> new IllegalArgumentException("Assignment 2 not found"));

        // 가용성 체크
        Participant p1 = participantRepository.findById(a1.getParticipantId()).get();
        Participant p2 = participantRepository.findById(a2.getParticipantId()).get();

        if (!p1.isAvailable(a2.getAssignedDate()) || !p2.isAvailable(a1.getAssignedDate())) {
            throw new IllegalStateException("One of the participants is not available on the other date");
        }

        // 참여자 교체
        Long tempPId = a1.getParticipantId();
        a1.setParticipantId(a2.getParticipantId());
        a2.setParticipantId(tempPId);

        // 상태를 MANUAL_FIXED로 변경하여 자동 재배분 시 보호
        a1.setStatus(AssignmentStatus.MANUAL_FIXED);
        a2.setStatus(AssignmentStatus.MANUAL_FIXED);

        assignmentRepository.save(a1);
        assignmentRepository.save(a2);
    }
}
