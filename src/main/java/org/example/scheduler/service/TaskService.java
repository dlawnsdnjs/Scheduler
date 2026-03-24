package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.repository.ParticipantRepository;
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
    public void updateConflicts(Long taskId, List<Long> conflictTaskIds) {
        TaskDefinition task = findById(taskId);
        if (conflictTaskIds == null) {
            task.setConflictingTasks(new ArrayList<>());
        } else {
            task.setConflictingTasks(taskRepository.findAllById(conflictTaskIds));
        }
    }
}
