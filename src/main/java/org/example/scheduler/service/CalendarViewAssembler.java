package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.dto.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CalendarViewAssembler {

    public CalendarViewModel assemble(int year, int month, Long filterTaskId, Long filterParticipantId,
                                      List<CalendarAssignmentDto> assignments, List<TaskDefinition> allTasks, List<Participant> participants) {
        
        LocalDate firstDay = LocalDate.of(year, month, 1);
        int dayOfWeekValue = (firstDay.getDayOfWeek().getValue() == 7) ? 0 : firstDay.getDayOfWeek().getValue();

        List<List<LocalDate>> weeks = new ArrayList<>();
        List<LocalDate> currentWeek = new ArrayList<>();
        for (int i = 0; i < dayOfWeekValue; i++) currentWeek.add(null);
        int lengthOfMonth = firstDay.lengthOfMonth();
        for (int day = 1; day <= lengthOfMonth; day++) {
            currentWeek.add(LocalDate.of(year, month, day));
            if (currentWeek.size() == 7) { weeks.add(currentWeek); currentWeek = new ArrayList<>(); }
        }
        if (!currentWeek.isEmpty()) { while (currentWeek.size() < 7) currentWeek.add(null); weeks.add(currentWeek); }

        Map<String, List<CalendarAssignmentDto.AssignmentDetailDto>> assignmentsMap = assignments.stream()
                .collect(Collectors.toMap(a -> a.getDate().toString(), CalendarAssignmentDto::getAssignments, (e, r) -> e));

        Map<Long, List<ParticipantStatsDto>> taskCycleStats = allTasks.stream().collect(Collectors.toMap(
                TaskDefinition::getId,
                task -> task.getAllowedParticipants().stream()
                        .map(p -> new ParticipantStatsDto(p.getName(), p.getTaskCount(task.getId()), p.getLastDate(task.getId())))
                        .sorted(Comparator.comparing(ParticipantStatsDto::getLastDate, Comparator.nullsFirst(LocalDate::compareTo))
                                .thenComparingInt(ParticipantStatsDto::getCount))
                        .collect(Collectors.toList())
        ));

        LocalDate current = LocalDate.of(year, month, 1);
        LocalDate prev = current.minusMonths(1);
        LocalDate next = current.plusMonths(1);

        return CalendarViewModel.builder()
                .year(year).month(month)
                .calendarWeeks(weeks)
                .assignmentsMap(assignmentsMap)
                .tasks(allTasks)
                .participants(participants)
                .taskCycleStats(taskCycleStats)
                .prevYear(prev.getYear()).prevMonth(prev.getMonthValue())
                .nextYear(next.getYear()).nextMonth(next.getMonthValue())
                .filterTaskId(filterTaskId).filterParticipantId(filterParticipantId)
                .build();
    }
}
