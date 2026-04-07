package org.example.scheduler.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.domain.Participant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class CalendarViewModel {
    private final int year;
    private final int month;
    private final List<List<LocalDate>> calendarWeeks;
    private final Map<String, List<CalendarAssignmentDto.AssignmentDetailDto>> assignmentsMap;
    private final List<TaskDefinition> tasks;
    private final List<Participant> participants;
    private final Map<Long, List<ParticipantStatsDto>> taskCycleStats;
    private final int prevYear, prevMonth, nextYear, nextMonth;
    private final Long filterTaskId, filterParticipantId;
}
