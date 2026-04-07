package org.example.scheduler.controller;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.dto.CalendarAssignmentDto;
import org.example.scheduler.service.DistributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final DistributionService distributionService;

    @PostMapping("/distribute")
    public ResponseEntity<Void> distribute(@RequestParam(name = "taskId") List<Long> taskIds, @RequestParam int year, @RequestParam int month) {
        distributionService.distribute(taskIds, year, month);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/calendar")
    public ResponseEntity<List<CalendarAssignmentDto>> getCalendar(
            @RequestParam int year, 
            @RequestParam int month,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long participantId) {
        return ResponseEntity.ok(distributionService.getCalendarAssignments(year, month, taskId, participantId));
    }

    @PostMapping("/swap")
    public ResponseEntity<Void> swap(@RequestParam Long assignmentId1, @RequestParam Long assignmentId2) {
        distributionService.swapAssignments(assignmentId1, assignmentId2);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reassign")
    public ResponseEntity<Void> reassign(@RequestParam Long assignmentId) {
        distributionService.reassignAssignment(assignmentId);
        return ResponseEntity.ok().build();
    }
}
