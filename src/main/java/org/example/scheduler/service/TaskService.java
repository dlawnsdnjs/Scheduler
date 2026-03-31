package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.repository.ParticipantRepository;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskDefinitionRepository taskRepository;
    private final ParticipantRepository participantRepository;
    private final ScheduleAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public List<TaskDefinition> findAll() {
        return taskRepository.findAll();
    }

    @Transactional(readOnly = true)
    public TaskDefinition findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("업무를 찾을 수 없습니다. ID: " + id));
    }

    @Transactional
    public void addTask(String name, String cycleType, String cycleValue, int required, String color, List<Long> participantIds) {
        TaskDefinition task = new TaskDefinition(name, cycleType, cycleValue, required);
        task.setColor(color);
        if (participantIds != null) {
            task.setAllowedParticipants(participantRepository.findAllById(participantIds));
        }
        taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        assignmentRepository.deleteAll(assignmentRepository.findByTaskId(id));
        taskRepository.deleteById(id);
    }

    @Transactional
    public void updateParticipants(Long taskId, List<Long> participantIds) {
        TaskDefinition task = findById(taskId);
        if (participantIds == null) {
            task.setAllowedParticipants(new ArrayList<>());
        } else {
            task.setAllowedParticipants(participantRepository.findAllById(participantIds));
        }
    }
@Transactional
public void updateTask(Long taskId, String name, String cycleType, String cycleValue, int required, String color) {
    TaskDefinition task = findById(taskId);
    task.setTaskName(name);
    task.setCycleType(cycleType);
    task.setCycleValue(cycleValue);
    task.setRequiredParticipantsPerDay(required);
    task.setColor(color);
}

@Transactional
public void updateConflicts(Long taskId, List<Long> conflictTaskIds) {
    TaskDefinition task = findById(taskId);
    List<TaskDefinition> newConflicts = (conflictTaskIds == null) ? new ArrayList<>() : taskRepository.findAllById(conflictTaskIds);

    // 1. 기존 관계 중 제거된 것들에 대해 역방향 관계도 제거
    for (TaskDefinition oldConflict : new ArrayList<>(task.getConflictingTasks())) {
        if (!newConflicts.contains(oldConflict)) {
            oldConflict.getConflictingTasks().remove(task); // 상대방 쪽에서도 나를 제거
        }
    }

    // 2. 새로운 관계들에 대해 역방향 관계도 추가
    for (TaskDefinition newConflict : newConflicts) {
        if (!newConflict.getConflictingTasks().contains(task)) {
            newConflict.getConflictingTasks().add(task); // 상대방 쪽에서도 나를 추가
        }
    }

    // 3. 내 목록 업데이트
    task.setConflictingTasks(newConflicts);
    taskRepository.save(task);
}

}

