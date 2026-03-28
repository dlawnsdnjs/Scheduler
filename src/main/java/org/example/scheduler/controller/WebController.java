package org.example.scheduler.controller;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.dto.CalendarAssignmentDto;
import org.example.scheduler.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final DistributionService distributionService;
    private final TaskService taskService;
    private final ParticipantService participantService;
    private final DataMigrationService dataMigrationService;

    @GetMapping("/")
    public String index(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long filterTaskId,
            @RequestParam(required = false) Long filterParticipantId,
            Model model) {
        
        LocalDate now = LocalDate.now();
        int targetYear = (year == null) ? now.getYear() : year;
        int targetMonth = (month == null) ? now.getMonthValue() : month;

        LocalDate firstDay = LocalDate.of(targetYear, targetMonth, 1);
        int dayOfWeekValue = firstDay.getDayOfWeek().getValue(); 
        if (dayOfWeekValue == 7) dayOfWeekValue = 0; 

        List<List<LocalDate>> weeks = new ArrayList<>();
        List<LocalDate> currentWeek = new ArrayList<>();
        for (int i = 0; i < dayOfWeekValue; i++) currentWeek.add(null);
        int lengthOfMonth = firstDay.lengthOfMonth();
        for (int day = 1; day <= lengthOfMonth; day++) {
            currentWeek.add(LocalDate.of(targetYear, targetMonth, day));
            if (currentWeek.size() == 7) { weeks.add(currentWeek); currentWeek = new ArrayList<>(); }
        }
        if (!currentWeek.isEmpty()) { while (currentWeek.size() < 7) currentWeek.add(null); weeks.add(currentWeek); }

        List<CalendarAssignmentDto> assignments = distributionService.getCalendarAssignments(targetYear, targetMonth, filterTaskId, filterParticipantId);
        Map<String, List<CalendarAssignmentDto.AssignmentDetailDto>> assignmentsMap = assignments.stream()
                .collect(Collectors.toMap(a -> a.getDate().toString(), CalendarAssignmentDto::getAssignments));

        model.addAttribute("year", targetYear);
        model.addAttribute("month", targetMonth);
        model.addAttribute("calendarWeeks", weeks);
        model.addAttribute("assignmentsMap", assignmentsMap);
        
        model.addAttribute("tasks", taskService.findAll());
        model.addAttribute("participants", participantService.findAll());
        model.addAttribute("filterTaskId", filterTaskId);
        model.addAttribute("filterParticipantId", filterParticipantId);

        // 업무별 사이클 현황 데이터 (Key를 String으로 변환하여 JSP 호환성 확보)
        List<org.example.scheduler.domain.TaskDefinition> allTasks = taskService.findAll();
        Map<String, List<org.example.scheduler.dto.ParticipantStatsDto>> taskCycleStats = allTasks.stream().collect(Collectors.toMap(
                task -> task.getId().toString(),
                task -> task.getAllowedParticipants().stream()
                        .map(p -> {
                            org.example.scheduler.dto.ParticipantStatsDto dto = new org.example.scheduler.dto.ParticipantStatsDto();
                            dto.setName(p.getName());
                            dto.setCount(p.getTaskCount(task.getId()));
                            dto.setLastDate(p.getLastDate(task.getId()));
                            return dto;
                        })
                        .sorted(java.util.Comparator.comparing((org.example.scheduler.dto.ParticipantStatsDto d) -> d.getLastDate())
                                .thenComparing(org.example.scheduler.dto.ParticipantStatsDto::getCount))
                        .collect(Collectors.toList())
        ));
        model.addAttribute("taskCycleStats", taskCycleStats);

        LocalDate currentMonth = LocalDate.of(targetYear, targetMonth, 1);
        LocalDate prev = currentMonth.minusMonths(1);
        LocalDate next = currentMonth.plusMonths(1);
        model.addAttribute("prevYear", prev.getYear());
        model.addAttribute("prevMonth", prev.getMonthValue());
        model.addAttribute("nextYear", next.getYear());
        model.addAttribute("nextMonth", next.getMonthValue());

        return "index";
    }

    @PostMapping("/assignments/manual")
    public String manualAssign(Long taskId, String date, Long participantId, int year, int month) {
        distributionService.manualAssign(taskId, LocalDate.parse(date), participantId);
        return "redirect:/?year=" + year + "&month=" + month;
    }

    @PostMapping("/assignments/add")
    public String addManualAssignment(Long taskId, String date, Long participantId, int year, int month) {
        distributionService.addManualAssignment(taskId, LocalDate.parse(date), participantId);
        return "redirect:/?year=" + year + "&month=" + month;
    }

    // --- 업무 관리 ---
    @GetMapping("/tasks")
    public String tasks(Model model) {
        model.addAttribute("tasks", taskService.findAll());
        model.addAttribute("allParticipants", participantService.findAll());
        return "tasks";
    }

    @PostMapping("/tasks/add")
    public String addTask(String taskName, String cycleType, String cycleValue, int requiredParticipantsPerDay, String color,
                          @RequestParam(required = false) List<Long> participantIds) {
        taskService.addTask(taskName, cycleType, cycleValue, requiredParticipantsPerDay, color, participantIds);
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/update")
    public String updateTask(Long taskId, String taskName, String cycleType, String cycleValue, int requiredParticipantsPerDay, String color) {
        taskService.updateTask(taskId, taskName, cycleType, cycleValue, requiredParticipantsPerDay, color);
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/delete")
    public String deleteTask(Long taskId) {
        taskService.deleteTask(taskId);
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/updateParticipants")
    public String updateParticipants(Long taskId, @RequestParam(required = false) List<Long> participantIds) {
        taskService.updateParticipants(taskId, participantIds);
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/updateConflicts")
    public String updateConflicts(Long taskId, @RequestParam(required = false) List<Long> conflictTaskIds) {
        taskService.updateConflicts(taskId, conflictTaskIds);
        return "redirect:/tasks";
    }

    // --- 참여자 관리 ---
    @GetMapping("/participants")
    public String participants(Model model) {
        model.addAttribute("participants", participantService.findAll());
        return "participants";
    }

    @PostMapping("/participants/add")
    public String addParticipant(String name, String joinDate) {
        participantService.addParticipant(name, LocalDate.parse(joinDate));
        return "redirect:/participants";
    }

    @PostMapping("/participants/delete")
    public String deleteParticipant(Long participantId) {
        participantService.deleteParticipant(participantId);
        return "redirect:/participants";
    }

    // --- 참여자 상세 관리 (불참/규칙) ---
    @GetMapping("/participants/detail")
    public String participantDetail(Long id, Model model) {
        model.addAttribute("p", participantService.findById(id));
        return "participant_detail";
    }

    @PostMapping("/participants/addRange")
    public String addUnavailableRange(Long participantId, String startDate, String endDate) {
        participantService.addUnavailableRange(participantId, LocalDate.parse(startDate), LocalDate.parse(endDate));
        return "redirect:/participants/detail?id=" + participantId;
    }

    @PostMapping("/participants/addRule")
    public String addAvailabilityRule(Long participantId, String ruleType, String ruleValue) {
        participantService.addAvailabilityRule(participantId, ruleType, ruleValue);
        return "redirect:/participants/detail?id=" + participantId;
    }

    @PostMapping("/participants/deleteRange")
    public String deleteUnavailableRange(Long id, Long participantId) {
        participantService.deleteUnavailableRange(id);
        return "redirect:/participants/detail?id=" + participantId;
    }

    @PostMapping("/participants/deleteRule")
    public String deleteAvailabilityRule(Long id, Long participantId) {
        participantService.deleteAvailabilityRule(id);
        return "redirect:/participants/detail?id=" + participantId;
    }

    // --- 일정 분배 실행 ---
    @GetMapping("/distribute")
    public String distributePage(Model model) {
        model.addAttribute("tasks", taskService.findAll());
        model.addAttribute("now", LocalDate.now());
        return "distribute";
    }

    @PostMapping("/distribute/run")
    public String runDistribution(Long taskId, int year, int month) {
        distributionService.distribute(taskId, year, month);
        return "redirect:/?year=" + year + "&month=" + month;
    }

    @PostMapping("/distribute/runCustom")
    public String runCustomDistribution(Long taskId, String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        distributionService.distribute(taskId, start, end);
        return "redirect:/?year=" + start.getYear() + "&month=" + start.getMonthValue();
    }

    @PostMapping("/distribute/clear")
    public String clearAssignments(@RequestParam(required = false) Long taskId, String startDate, String endDate) {
        distributionService.clearAssignments(taskId, LocalDate.parse(startDate), LocalDate.parse(endDate));
        return "redirect:/distribute";
    }

    @PostMapping("/assignments/delete")
    public String deleteAssignment(Long id, int year, int month) {
        distributionService.deleteAssignment(id);
        return "redirect:/?year=" + year + "&month=" + month;
    }

    @PostMapping("/assignments/cancel")
    public String cancelAndReplace(Long id, int year, int month) {
        distributionService.cancelAndReplace(id);
        return "redirect:/?year=" + year + "&month=" + month;
    }

    // --- 통계 및 일정 데이터 내보내기/가져오기 ---
    @GetMapping("/stats/export")
    public ResponseEntity<byte[]> exportStats(@RequestParam String startDate, @RequestParam String endDate) {
        String json = dataMigrationService.exportFullDataJson(LocalDate.parse(startDate), LocalDate.parse(endDate));
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scheduler_backup_" + startDate + "_" + endDate + ".json\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @GetMapping("/stats/manage")
    public String manageStats(Model model) {
        model.addAttribute("now", LocalDate.now());
        return "manage_stats";
    }

    @PostMapping("/stats/import")
    public String importStats(@RequestParam("file") MultipartFile file) throws Exception {
        if (!file.isEmpty()) {
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            dataMigrationService.importFullDataJson(json);
        }
        return "redirect:/stats/manage";
    }
}
