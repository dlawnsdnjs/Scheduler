<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${p.name} 상세 정보 - 공정 일정 스케줄러</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 800px; margin: auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        .section { border-bottom: 1px solid #ddd; padding-bottom: 20px; margin-bottom: 20px; }
        .btn { padding: 8px 12px; background: #333; color: #fff; border: none; border-radius: 5px; cursor: pointer; text-decoration: none; }
        input, select { padding: 8px; margin-right: 10px; }
        ul { list-style: none; padding: 0; }
        li { padding: 5px 0; border-bottom: 1px dashed #eee; }
    </style>
</head>
<body>
    <div class="container">
        <h1>참여자 상세 정보: ${p.name}</h1>
        <a href="/participants" class="btn">목록으로 돌아가기</a>

        <div class="section" style="margin-top:20px;">
            <h3>기본 정보</h3>
            <p><strong>입사일:</strong> ${p.joinDate}</p>
            <p><strong>퇴사일:</strong> ${p.leaveDate != null ? p.leaveDate : '재직 중'}</p>
        </div>

        <div class="section">
            <h3>불참 기간 설정 (Range)</h3>
            <form action="/participants/addRange" method="post">
                <input type="hidden" name="participantId" value="${p.id}">
                시작: <input type="date" name="startDate" required>
                종료: <input type="date" name="endDate" required>
                <button type="submit" class="btn" style="background:#28a745;">기간 추가</button>
            </form>
            <h4>현재 설정된 기간</h4>
            <ul>
                <c:forEach var="range" items="${p.unavailableRanges}">
                    <li>${range.startDate} ~ ${range.endDate}</li>
                </c:forEach>
                <c:if test="${empty p.unavailableRanges}"><li>등록된 기간이 없습니다.</li></c:if>
            </ul>
        </div>

        <div class="section">
            <h3>가용 규칙 설정 (Availability Rules)</h3>
            <form action="/participants/addRule" method="post" id="ruleForm">
                <input type="hidden" name="participantId" value="${p.id}">
                <select name="ruleType" id="ruleType">
                    <option value="EVEN_DAYS">짝수 날짜만 가능</option>
                    <option value="ODD_DAYS">홀수 날짜만 가능</option>
                    <option value="WEEKDAYS_ONLY">평일만 가능 (월~금)</option>
                    <option value="WEEKENDS_ONLY">주말만 가능 (토~일)</option>
                    <option value="N_DAY_CYCLE">N일 주기 설정</option>
                </select>
                <span id="nDayFields" style="display:none;">
                    기준일: <input type="date" id="baseDate" style="width:130px;">
                    주기: <input type="number" id="cycleDays" placeholder="일" style="width:50px;" min="1">
                    <input type="hidden" name="ruleValue" id="ruleValue">
                </span>
                <button type="submit" class="btn" style="background:#28a745;">규칙 추가</button>
            </form>
            <script>
                document.getElementById('ruleType').addEventListener('change', function() {
                    document.getElementById('nDayFields').style.display = (this.value === 'N_DAY_CYCLE') ? 'inline' : 'none';
                });
                document.getElementById('ruleForm').addEventListener('submit', function(e) {
                    if (document.getElementById('ruleType').value === 'N_DAY_CYCLE') {
                        const baseDate = document.getElementById('baseDate').value;
                        const cycleDays = document.getElementById('cycleDays').value;
                        if (!baseDate || !cycleDays) {
                            alert('기준일과 주기를 입력하세요.');
                            e.preventDefault();
                            return;
                        }
                        document.getElementById('ruleValue').value = baseDate + ':' + cycleDays;
                    }
                });
            </script>
            <h4>현재 설정된 규칙</h4>
            <ul>
                <c:forEach var="rule" items="${p.availabilityRules}">
                    <li>
                        <c:choose>
                            <c:when test="${rule.ruleType == 'EVEN_DAYS'}">짝수 날짜 근무</c:when>
                            <c:when test="${rule.ruleType == 'ODD_DAYS'}">홀수 날짜 근무</c:when>
                            <c:when test="${rule.ruleType == 'WEEKDAYS_ONLY'}">평일만 근무</c:when>
                            <c:when test="${rule.ruleType == 'WEEKENDS_ONLY'}">주말만 근무</c:when>
                            <c:when test="${rule.ruleType == 'N_DAY_CYCLE'}">
                                ${rule.ruleValue.split(':')[1]}일 주기 근무 (기준: ${rule.ruleValue.split(':')[0]})
                            </c:when>
                            <c:otherwise>${rule.ruleType} (${rule.ruleValue})</c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
                <c:if test="${empty p.availabilityRules}"><li>등록된 규칙이 없습니다. (모든 날짜 가용)</li></c:if>
            </ul>
        </div>
    </div>
</body>
</html>
