package org.example.scheduler.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TaskDefinitionTest {
    @Test
    void testGetTargetDatesWeekly() {
        TaskDefinition task = new TaskDefinition("WeeklyTask", CycleType.WEEKLY, "월,수,금", 1);
        LocalDate start = LocalDate.of(2026, 4, 1); // 수요일
        LocalDate end = LocalDate.of(2026, 4, 7);   // 화요일
        
        List<LocalDate> dates = task.getTargetDates(start, end);
        
        // 4/1(수), 4/3(금), 4/6(월) -> 총 3일
        assertThat(dates).hasSize(3);
        assertThat(dates).contains(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 6));
    }

    @Test
    void testGetTargetDatesInterval() {
        TaskDefinition task = new TaskDefinition("IntervalTask", CycleType.INTERVAL, "2", 1);
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 5);
        
        List<LocalDate> dates = task.getTargetDates(start, end);
        
        // 4/1, 4/3, 4/5 -> 총 3일
        assertThat(dates).hasSize(3);
        assertThat(dates).contains(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 5));
    }
}
