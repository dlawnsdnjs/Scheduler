<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>일정 자동 분배 - 공정 일정 스케줄러</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 700px; margin: auto; background: #fff; padding: 25px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        h1 { color: #333; text-align: center; }
        .mode-selector { display: flex; gap: 10px; margin-bottom: 30px; justify-content: center; }
        .mode-btn { padding: 10px 20px; background: #eee; border: 1px solid #ccc; cursor: pointer; border-radius: 5px; font-weight: bold; }
        .mode-btn.active { background: #007bff; color: #fff; border-color: #0056b3; }
        .form-section { display: none; border: 1px solid #ddd; padding: 20px; border-radius: 8px; }
        .form-section.active { display: block; }
        .form-group { margin-bottom: 20px; }
        label { display: block; margin-bottom: 8px; font-weight: bold; }
        select, input:not([type="checkbox"]) { width: 100%; padding: 10px; box-sizing: border-box; border: 1px solid #ddd; border-radius: 4px; }
        input[type="checkbox"] { width: auto; cursor: pointer; }
        .btn { padding: 12px 20px; background: #28a745; color: #fff; border: none; border-radius: 5px; cursor: pointer; width: 100%; font-size: 1.1em; font-weight: bold; }
        .info-box { background-color: #e9ecef; padding: 15px; border-radius: 5px; margin-bottom: 20px; font-size: 0.9em; line-height: 1.5; }
    </style>
</head>
<body>
    <div class="container">
        <h1>일정 자동 분배</h1>
        <a href="/" style="display:inline-block; margin-bottom:20px; color:#666; text-decoration:none;">← 달력으로 돌아가기</a>

        <div class="mode-selector">
            <button class="mode-btn active" onclick="switchMode('month')">한달 단위 배분</button>
            <button class="mode-btn" onclick="switchMode('custom')">자유 기간 배분</button>
        </div>

        <!-- 한달 단위 배분 폼 -->
        <div id="monthMode" class="form-section active">
            <div class="info-box">지정된 연월의 모든 수행 일정을 공정하게 배분합니다.</div>
            <form action="/distribute/run" method="post">
                <div class="form-group">
                    <label>배분할 업무 선택 (여러 개 선택 가능):</label>
                    <div style="background:#fff; border:1px solid #ddd; padding:10px; border-radius:4px; max-height:200px; overflow-y:auto;">
                        <div style="margin-bottom:8px; padding-bottom:5px; border-bottom:1px solid #eee;">
                            <label style="font-weight:normal; cursor:pointer; display:flex; align-items:center; gap:8px;">
                                <input type="checkbox" id="selectAllMonth" onclick="toggleAll('month')"> <strong>전체 선택</strong>
                            </label>
                        </div>
                        <c:forEach var="task" items="${tasks}">
                            <label style="font-weight:normal; cursor:pointer; display:flex; align-items:center; gap:8px; margin-bottom:4px;">
                                <input type="checkbox" name="taskIds" value="${task.id}" class="task-check-month"> ${task.taskName}
                            </label>
                        </c:forEach>
                    </div>
                </div>
                <div style="display:flex; gap:10px;">
                    <div class="form-group" style="flex:1;">
                        <label>연도:</label>
                        <input type="number" name="year" value="${now.year}" required>
                    </div>
                    <div class="form-group" style="flex:1;">
                        <label>월:</label>
                        <input type="number" name="month" value="${now.monthValue}" min="1" max="12" required>
                    </div>
                </div>
                <button type="submit" class="btn">한달 일정 생성</button>
            </form>
        </div>

        <!-- 자유 기간 배분 폼 -->
        <div id="customMode" class="form-section">
            <div class="info-box">원하는 시작일과 종료일을 지정하여 해당 기간의 일정을 배분합니다.</div>
            <form action="/distribute/runCustom" method="post">
                <div class="form-group">
                    <label>배분할 업무 선택 (여러 개 선택 가능):</label>
                    <div style="background:#fff; border:1px solid #ddd; padding:10px; border-radius:4px; max-height:200px; overflow-y:auto;">
                        <div style="margin-bottom:8px; padding-bottom:5px; border-bottom:1px solid #eee;">
                            <label style="font-weight:normal; cursor:pointer; display:flex; align-items:center; gap:8px;">
                                <input type="checkbox" id="selectAllCustom" onclick="toggleAll('custom')"> <strong>전체 선택</strong>
                            </label>
                        </div>
                        <c:forEach var="task" items="${tasks}">
                            <label style="font-weight:normal; cursor:pointer; display:flex; align-items:center; gap:8px; margin-bottom:4px;">
                                <input type="checkbox" name="taskIds" value="${task.id}" class="task-check-custom"> ${task.taskName}
                            </label>
                        </c:forEach>
                    </div>
                </div>
                <div class="form-group">
                    <label>시작일:</label>
                    <input type="date" name="startDate" value="${now}" required>
                </div>
                <div class="form-group">
                    <label>종료일:</label>
                    <input type="date" name="endDate" id="customEndDate" required>
                </div>
                <button type="submit" class="btn" style="background:#007bff;">기간 일정 생성</button>
            </form>
        </div>

        <hr style="margin: 40px 0; border: 0; border-top: 1px solid #eee;">

        <!-- 일정 초기화 섹션 -->
        <div class="section">
            <h2 style="color:#dc3545;">일정 초기화 (Clear)</h2>
            <div class="info-box" style="background-color:#fff5f5; border-color:#feb2b2;">
                지정한 기간의 배정 일정을 한 번에 삭제합니다. 신중하게 사용하세요.
            </div>
            <form action="/distribute/clear" method="post">
                <div class="form-group">
                    <label>초기화할 업무:</label>
                    <select name="taskId">
                        <option value="">전체 업무 초기화</option>
                        <c:forEach var="task" items="${tasks}">
                            <option value="${task.id}">${task.taskName}</option>
                        </c:forEach>
                    </select>
                </div>
                <div style="display:flex; gap:10px;">
                    <div class="form-group" style="flex:1;">
                        <label>시작일:</label>
                        <input type="date" name="startDate" value="${now.withDayOfMonth(1)}" required>
                    </div>
                    <div class="form-group" style="flex:1;">
                        <label>종료일:</label>
                        <input type="date" name="endDate" value="${now.withDayOfMonth(now.lengthOfMonth())}" required>
                    </div>
                </div>
                <button type="submit" class="btn" style="background:#dc3545;" onclick="return confirm('지정된 기간의 일정을 정말로 삭제하시겠습니까?');">선택한 일정 초기화</button>
            </form>
        </div>
    </div>

    <script>
        function switchMode(mode) {
            document.querySelectorAll('.mode-btn').forEach(btn => btn.classList.remove('active'));
            document.querySelectorAll('.form-section').forEach(sec => btn = sec.classList.remove('active'));
            
            if (mode === 'month') {
                document.querySelector('.mode-btn:nth-child(1)').classList.add('active');
                document.getElementById('monthMode').classList.add('active');
            } else {
                document.querySelector('.mode-btn:nth-child(2)').classList.add('active');
                document.getElementById('customMode').classList.add('active');
            }
        }

        window.onload = function() {
            var now = new Date();
            var lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);
            document.getElementById('customEndDate').value = lastDay.toISOString().split('T')[0];
        }

        function toggleAll(mode) {
            const selectAll = document.getElementById(mode === 'month' ? 'selectAllMonth' : 'selectAllCustom');
            const checks = document.querySelectorAll(mode === 'month' ? '.task-check-month' : '.task-check-custom');
            checks.forEach(c => c.checked = selectAll.checked);
        }
    </script>
</body>
</html>
