<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>공정 일정 스케줄러</title>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; padding: 20px; background-color: #f0f2f5; color: #1c1e21; }
        .container { max-width: 1300px; margin: auto; background: #fff; padding: 25px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.08); }
        h1 { text-align: center; margin-bottom: 20px; color: #1877f2; }
        
        /* 네비게이션 및 필터 */
        .nav-links { margin-bottom: 20px; text-align: center; border-bottom: 1px solid #ddd; padding-bottom: 15px; }
        .nav-links a { margin: 0 12px; text-decoration: none; color: #4b4f56; font-weight: 600; font-size: 0.95em; }
        .nav-links a:hover { color: #1877f2; }
        
        .filter-section { display: flex; justify-content: center; gap: 20px; margin-bottom: 20px; background: #f8f9fa; padding: 15px; border-radius: 8px; }
        .filter-section select { padding: 8px; border-radius: 5px; border: 1px solid #ccc; }

        /* 범례 (Legend) */
        .legend { display: flex; flex-wrap: wrap; justify-content: center; gap: 15px; margin-bottom: 20px; padding: 10px; border: 1px dashed #ccc; border-radius: 8px; }
        .legend-item { display: flex; align-items: center; gap: 8px; font-size: 0.9em; font-weight: bold; }
        .color-box { width: 16px; height: 16px; border-radius: 3px; }

        /* 달력 테이블 */
        table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        th, td { border: 1px solid #e1e4e8; padding: 8px; text-align: left; vertical-align: top; height: 110px; }
        th { background-color: #f1f3f5; text-align: center; padding: 10px; font-weight: 700; color: #4b4f56; }
        .date-num { font-weight: 800; font-size: 1.1em; color: #8d949e; margin-bottom: 8px; display: block; }
        
        /* 배정 항목 스타일 */
        .assignment { font-size: 0.8em; margin-bottom: 4px; padding: 4px 8px; border-radius: 4px; color: #fff; position: relative; border-left: 4px solid rgba(0,0,0,0.2); }
        .assignment:hover .action-btns { display: flex; }
        .participant-name { cursor: pointer; text-decoration: underline dotted; }
        
        .action-btns { display: none; position: absolute; right: 2px; top: 2px; background: rgba(0,0,0,0.6); border-radius: 3px; gap: 2px; }
        .action-btns button { background: none; border: none; color: #fff; cursor: pointer; font-size: 1.1em; padding: 0 4px; }
        
        /* 수동 배정 폼 */
        .manual-form { margin-top: 5px; font-size: 0.8em; display: none; background: #fff; border: 1px solid #ddd; padding: 5px; border-radius: 4px; }
        .add-btn { background: #e7f3ff; color: #1877f2; border: none; border-radius: 4px; cursor: pointer; font-weight: bold; font-size: 1.1em; padding: 0 6px; line-height: 1; vertical-align: middle; }
        .add-btn:hover { background: #d0e7ff; }

        .btn { display: inline-block; padding: 8px 16px; background: #1877f2; color: #fff; text-decoration: none; border-radius: 6px; border: none; cursor: pointer; font-weight: 600; }
        .btn:hover { background: #166fe5; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Schedule Dashboard</h1>
        
        <div class="nav-links">
            <a href="/tasks">업무 관리</a> | 
            <a href="/participants">참여자 관리</a> | 
            <a href="/distribute">일정 자동 분배</a> |
            <a href="/stats/manage">데이터 백업/복구</a>
        </div>

        <!-- 업무별 사이클 현황 -->
        <div class="cycle-status-section" style="margin-bottom: 30px; background: #fff; border: 1px solid #e1e4e8; border-radius: 8px; padding: 15px;">
            <h3 style="margin-top: 0; color: #1877f2; border-bottom: 2px solid #f0f2f5; padding-bottom: 10px;">업무별 사이클 현황 (다음 배정 우선순위)</h3>
            <div style="display: flex; flex-wrap: wrap; gap: 20px;">
                <c:forEach var="task" items="${tasks}">
                    <div style="flex: 1; min-width: 280px; border: 1px solid #eee; border-radius: 6px; padding: 10px;">
                        <h4 style="margin: 0 0 10px 0; display: flex; align-items: center; gap: 8px;">
                            <div class="color-box" style="background-color: ${empty task.color ? '#666' : task.color}"></div>
                            ${task.taskName}
                        </h4>
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.85em; height: auto;">
                            <thead style="background: #f8f9fa;">
                                <tr>
                                    <th style="height: auto; padding: 5px; text-align: left;">이름</th>
                                    <th style="height: auto; padding: 5px; text-align: center;">횟수</th>
                                    <th style="height: auto; padding: 5px; text-align: center;">마지막 배정일</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="stat" items="${taskCycleStats[task.id]}">
                                    <tr>
                                        <td style="height: auto; padding: 5px; border: 1px solid #eee;">${stat.name}</td>
                                        <td style="height: auto; padding: 5px; border: 1px solid #eee; text-align: center;">${stat.count}</td>
                                        <td style="height: auto; padding: 5px; border: 1px solid #eee; text-align: center;">
                                            <c:choose>
                                                <c:when test="${stat.lastDate == null || stat.lastDate.year < 0}">
                                                    -
                                                </c:when>
                                                <otherwise>
                                                    ${stat.lastDate}
                                                </otherwise>
                                            </c:choose>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:forEach>
            </div>
        </div>

        <!-- 범례 (Legend) -->
        <div class="legend">
            <c:forEach var="t" items="${tasks}">
                <div class="legend-item">
                    <div class="color-box" style="background-color: ${empty t.color ? '#666' : t.color}"></div>
                    <span>${t.taskName}</span>
                </div>
            </c:forEach>
        </div>

        <!-- 필터 섹션 -->
        <div class="filter-section">
            <form action="/" method="get" id="filterForm">
                <input type="hidden" name="year" value="${year}">
                <input type="hidden" name="month" value="${month}">
                
                업무 필터: 
                <select name="filterTaskId" onchange="this.form.submit()">
                    <option value="">전체 업무</option>
                    <c:forEach var="t" items="${tasks}">
                        <option value="${t.id}" ${filterTaskId == t.id ? 'selected' : ''}>${t.taskName}</option>
                    </c:forEach>
                </select>

                참여자 필터: 
                <select name="filterParticipantId" onchange="this.form.submit()">
                    <option value="">전체 참여자</option>
                    <c:forEach var="p" items="${participants}">
                        <option value="${p.id}" ${filterParticipantId == p.id ? 'selected' : ''}>${p.name}</option>
                    </c:forEach>
                </select>
                
                <a href="/" class="btn" style="background:#6c757d; padding:5px 10px; font-size:0.8em;">필터 초기화</a>
            </form>
        </div>

        <div class="calendar-header" style="display:flex; justify-content: space-between; align-items: center; margin-bottom: 15px;">
            <h2 style="margin:0;">${year}년 ${month}월</h2>
            <div>
                <a href="?year=${prevYear}&month=${prevMonth}&filterTaskId=${filterTaskId}&filterParticipantId=${filterParticipantId}" class="btn" style="background:#4b4f56;">◀</a>
                <a href="?year=${nextYear}&month=${nextMonth}&filterTaskId=${filterTaskId}&filterParticipantId=${filterParticipantId}" class="btn" style="background:#4b4f56;">▶</a>
            </div>
        </div>

        <table>
            <thead>
                <tr>
                    <th style="color:red;">Sun</th><th>Mon</th><th>Tue</th><th>Wed</th><th>Thu</th><th>Fri</th><th style="color:blue;">Sat</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="week" items="${calendarWeeks}">
                    <tr>
                        <c:forEach var="day" items="${week}">
                            <td>
                                <c:if test="${day != null}">
                                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 5px;">
                                        <span class="date-num">${day.dayOfMonth}</span>
                                        <button class="add-btn" onclick="toggleManualForm('add_form_${day.dayOfMonth}')" title="일정 추가">+</button>
                                    </div>
                                    
                                    <c:forEach var="assign" items="${assignmentsMap[day]}">
                                        <div class="assignment" style="background-color: ${empty assign.taskColor ? '#007bff' : assign.taskColor}">
                                            <span class="participant-name" onclick="toggleManualForm('form_${assign.assignmentId}')">
                                                ${assign.participantName}
                                            </span>
                                            
                                            <div class="action-btns">
                                                <form action="/assignments/cancel" method="post" style="display:inline;">
                                                    <input type="hidden" name="id" value="${assign.assignmentId}">
                                                    <input type="hidden" name="year" value="${year}">
                                                    <input type="hidden" name="month" value="${month}">
                                                    <button type="submit" title="불참처리 및 재배정">↻</button>
                                                </form>
                                                <form action="/assignments/delete" method="post" style="display:inline;">
                                                    <input type="hidden" name="id" value="${assign.assignmentId}">
                                                    <input type="hidden" name="year" value="${year}">
                                                    <input type="hidden" name="month" value="${month}">
                                                    <button type="submit" title="삭제">×</button>
                                                </form>
                                            </div>

                                            <!-- 참여자 개별 수정 폼 (클릭 시 나타남) -->
                                            <div id="form_${assign.assignmentId}" class="manual-form">
                                                <form action="/assignments/manual" method="post">
                                                    <input type="hidden" name="taskId" value="${assign.taskId}">
                                                    <input type="hidden" name="date" value="${day}">
                                                    <input type="hidden" name="year" value="${year}">
                                                    <input type="hidden" name="month" value="${month}">
                                                    <select name="participantId" style="width:100%; font-size:0.9em;" onchange="this.form.submit()">
                                                        <option value="">변경...</option>
                                                        <c:forEach var="p" items="${participants}">
                                                            <option value="${p.id}">${p.name}</option>
                                                        </c:forEach>
                                                    </select>
                                                </form>
                                            </div>
                                        </div>
                                    </c:forEach>

                                    <!-- 일정 추가 폼 -->
                                    <div id="add_form_${day.dayOfMonth}" class="manual-form" style="border: 1px solid #1877f2; margin-top: 5px;">
                                        <form action="/assignments/add" method="post">
                                            <input type="hidden" name="date" value="${day}">
                                            <input type="hidden" name="year" value="${year}">
                                            <input type="hidden" name="month" value="${month}">
                                            
                                            <select name="taskId" style="width:100%; margin-bottom:3px; font-size:0.85em;" required>
                                                <option value="">업무 선택...</option>
                                                <c:forEach var="t" items="${tasks}">
                                                    <option value="${t.id}">${t.taskName}</option>
                                                </c:forEach>
                                            </select>
                                            
                                            <select name="participantId" style="width:100%; margin-bottom:3px; font-size:0.85em;" required>
                                                <option value="">참여자 선택...</option>
                                                <c:forEach var="p" items="${participants}">
                                                    <option value="${p.id}">${p.name}</option>
                                                </c:forEach>
                                            </select>
                                            <button type="submit" class="btn" style="width:100%; padding:2px; font-size:0.8em;">추가</button>
                                        </form>
                                    </div>
                                </c:if>
                            </td>
                        </c:forEach>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>

    <script>
        function toggleManualForm(id) {
            var form = document.getElementById(id);
            form.style.display = (form.style.display === 'block') ? 'none' : 'block';
        }
    </script>
</body>
</html>
