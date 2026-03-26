<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>업무 관리 - 공정 일정 스케줄러</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 1000px; margin: auto; background: #fff; padding: 25px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        h1 { color: #333; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: left; vertical-align: top; }
        th { background-color: #f8f9fa; }
        .form-group { margin-bottom: 15px; }
        label { display: block; margin-bottom: 5px; font-weight: bold; }
        input, select { width: 100%; padding: 8px; box-sizing: border-box; }
        .btn { padding: 8px 12px; background: #333; color: #fff; border: none; border-radius: 5px; cursor: pointer; text-decoration: none; font-size: 0.9em; }
        .participant-list { border: 1px solid #ddd; padding: 10px; max-height: 120px; overflow-y: auto; background: #fafafa; }
        .participant-item { display: inline-block; margin-right: 10px; margin-bottom: 5px; font-size: 0.85em; }
        .participant-item input { width: auto; vertical-align: middle; }
        .edit-section { background: #fdfdfe; border: 1px solid #eee; padding: 10px; border-radius: 5px; margin-top: 10px; font-size: 0.9em; display: none; }
    </style>
</head>
<body>
    <div class="container">
        <h1>업무 관리</h1>
        <a href="/" class="btn">달력으로 돌아가기</a>

        <h2 style="margin-top:30px;">새 업무 등록</h2>
        <form action="/tasks/add" method="post">
            <div style="display:flex; gap:20px;">
                <div style="flex:1;">
                    <div class="form-group">
                        <label>업무명:</label>
                        <input type="text" name="taskName" required placeholder="예: 야간 당직">
                    </div>
                    <div class="form-group">
                        <label>주기 유형:</label>
                        <select name="cycleType" class="cycle-type-select">
                            <option value="WEEKLY">요일 지정 (WEEKLY)</option>
                            <option value="INTERVAL">N일 간격 (INTERVAL)</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>주기 값:</label>
                        <div class="weekly-cycle-div" style="display:block; border: 1px solid #ddd; padding: 5px; background: #fff;">
                            <label style="display:inline-block; margin-right:5px; font-weight:normal;"><input type="checkbox" class="day-checkbox" value="월" style="width:auto;"> 월</label>
                            <label style="display:inline-block; margin-right:5px; font-weight:normal;"><input type="checkbox" class="day-checkbox" value="화" style="width:auto;"> 화</label>
                            <label style="display:inline-block; margin-right:5px; font-weight:normal;"><input type="checkbox" class="day-checkbox" value="수" style="width:auto;"> 수</label>
                            <label style="display:inline-block; margin-right:5px; font-weight:normal;"><input type="checkbox" class="day-checkbox" value="목" style="width:auto;"> 목</label>
                            <label style="display:inline-block; margin-right:5px; font-weight:normal;"><input type="checkbox" class="day-checkbox" value="금" style="width:auto;"> 금</label>
                            <label style="display:inline-block; margin-right:5px; font-weight:normal;"><input type="checkbox" class="day-checkbox" value="토" style="width:auto;"> 토</label>
                            <label style="display:inline-block; margin-right:5px; font-weight:normal;"><input type="checkbox" class="day-checkbox" value="일" style="width:auto;"> 일</label>
                        </div>
                        <input type="text" class="interval-cycle-input" placeholder="예: 2" style="display:none;">
                        <input type="hidden" name="cycleValue" class="cycle-value-input" required>
                    </div>
                </div>
                <div style="flex:1;">
                    <div class="form-group">
                        <label>필요 인원(일별):</label>
                        <input type="number" name="requiredParticipantsPerDay" value="1" min="1">
                    </div>
                    <div class="form-group">
                        <label>업무 표시 색상:</label>
                        <input type="color" name="color" value="#007bff" style="height:40px; padding:2px;">
                    </div>
                    <div class="form-group">
                        <label>초기 참여 인원 선택:</label>
                        <div class="participant-list">
                            <c:forEach var="p" items="${allParticipants}">
                                <div class="participant-item">
                                    <input type="checkbox" name="participantIds" value="${p.id}" id="new_p_${p.id}">
                                    <label for="new_p_${p.id}" style="display:inline;">${p.name}</label>
                                </div>
                            </c:forEach>
                        </div>
                    </div>
                </div>
            </div>
            <button type="submit" class="btn" style="background:#28a745; width:100%; padding:12px;">등록하기</button>
        </form>

        <h2 style="margin-top:40px;">등록된 업무 목록</h2>
        <table>
            <thead>
                <tr>
                    <th width="200">업무 정보</th><th>참여자 관리</th><th>충돌 업무 관리</th><th>관리</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="task" items="${tasks}">
                    <tr>
                        <td>
                            <div style="border-left: 5px solid ${task.color}; padding-left: 10px;">
                                <strong>${task.taskName}</strong><br>
                                <small>${task.cycleType}: ${task.cycleValue}</small><br>
                                <small>필요: ${task.requiredParticipantsPerDay}명</small>
                            </div>
                            <button type="button" class="btn" style="padding:2px 5px; font-size:0.7em; margin-top:5px;" onclick="document.getElementById('edit_${task.id}').style.display='block'">수정</button>
                            <div id="edit_${task.id}" class="edit-section">
                                <form action="/tasks/update" method="post">
                                    <input type="hidden" name="taskId" value="${task.id}">
                                    <input type="text" name="taskName" value="${task.taskName}" required><br>
                                    <select name="cycleType" class="cycle-type-select">
                                        <option value="WEEKLY" ${task.cycleType == 'WEEKLY' ? 'selected' : ''}>WEEKLY</option>
                                        <option value="INTERVAL" ${task.cycleType == 'INTERVAL' ? 'selected' : ''}>INTERVAL</option>
                                    </select>
                                    <input type="hidden" name="cycleValue" class="cycle-value-input" value="${task.cycleValue}">
                                    <input type="number" name="requiredParticipantsPerDay" value="${task.requiredParticipantsPerDay}">
                                    <input type="color" name="color" value="${task.color}">
                                    <button type="submit" class="btn" style="background:#28a745;">저장</button>
                                    <button type="button" class="btn" style="background:#6c757d;" onclick="this.parentElement.parentElement.style.display='none'">취소</button>
                                </form>
                            </div>
                        </td>
                        <td>
                            <form action="/tasks/updateParticipants" method="post">
                                <input type="hidden" name="taskId" value="${task.id}">
                                <div class="participant-list" style="max-height:80px;">
                                    <c:forEach var="p" items="${allParticipants}">
                                        <c:set var="isAllowed" value="false" />
                                        <c:forEach var="ap" items="${task.allowedParticipants}">
                                            <c:if test="${ap.id == p.id}"><c:set var="isAllowed" value="true" /></c:if>
                                        </c:forEach>
                                        <div class="participant-item">
                                            <input type="checkbox" name="participantIds" value="${p.id}" id="task_${task.id}_p_${p.id}" ${isAllowed ? 'checked' : ''}>
                                            <label for="task_${task.id}_p_${p.id}" style="display:inline;">${p.name}</label>
                                        </div>
                                    </c:forEach>
                                </div>
                                <button type="submit" class="btn" style="background:#17a2b8; padding:3px 8px; margin-top:5px; font-size:0.75em;">참여자 저장</button>
                            </form>
                        </td>
                        <td>
                            <form action="/tasks/updateConflicts" method="post">
                                <input type="hidden" name="taskId" value="${task.id}">
                                <div class="participant-list" style="max-height:80px;">
                                    <c:forEach var="otherTask" items="${tasks}">
                                        <c:if test="${otherTask.id != task.id}">
                                            <c:set var="isConflict" value="false" />
                                            <c:forEach var="ct" items="${task.conflictingTasks}">
                                                <c:if test="${ct.id == otherTask.id}"><c:set var="isConflict" value="true" /></c:if>
                                            </c:forEach>
                                            <div class="participant-item">
                                                <input type="checkbox" name="conflictTaskIds" value="${otherTask.id}" id="task_${task.id}_c_${otherTask.id}" ${isConflict ? 'checked' : ''}>
                                                <label for="task_${task.id}_c_${otherTask.id}" style="display:inline;">${otherTask.taskName}</label>
                                            </div>
                                        </c:if>
                                    </c:forEach>
                                </div>
                                <button type="submit" class="btn" style="background:#ffc107; color:#333; padding:3px 8px; margin-top:5px; font-size:0.75em;">충돌 저장</button>
                            </form>
                        </td>
                        <td>
                            <form action="/tasks/delete" method="post" onsubmit="return confirm('삭제하시겠습니까?');">
                                <input type="hidden" name="taskId" value="${task.id}">
                                <button type="submit" class="btn" style="background:#dc3545; padding:5px 8px;">삭제</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>

    <script>
        document.querySelectorAll('.cycle-type-select').forEach(select => {
            select.addEventListener('change', function() {
                const parent = this.closest('form');
                const weekly = parent.querySelector('.weekly-cycle-div');
                const interval = parent.querySelector('.interval-cycle-input');
                if (weekly && interval) {
                    if (this.value === 'WEEKLY') { weekly.style.display = 'block'; interval.style.display = 'none'; }
                    else { weekly.style.display = 'none'; interval.style.display = 'block'; }
                }
            });
        });

        document.querySelectorAll('form').forEach(form => {
            form.addEventListener('submit', function(e) {
                const type = form.querySelector('.cycle-type-select');
                if(!type) return;
                const weekly = form.querySelector('.weekly-cycle-div');
                const interval = form.querySelector('.interval-cycle-input');
                const valInput = form.querySelector('.cycle-value-input');
                
                if (type.value === 'WEEKLY') {
                    const checked = Array.from(weekly.querySelectorAll('.day-checkbox:checked')).map(cb => cb.value);
                    valInput.value = checked.join(',');
                } else {
                    valInput.value = interval.value;
                }
            });
        });
    </script>
</body>
</html>
