<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${p.name} 상세 정보 - 공정 일정 스케줄러</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 800px; margin: auto; background: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        .section { border-bottom: 1px solid #ddd; padding-bottom: 20px; margin-bottom: 20px; }
        .btn { padding: 8px 12px; background: #333; color: #fff; border: none; border-radius: 5px; cursor: pointer; text-decoration: none; font-size: 0.9em; }
        input, select { padding: 8px; margin-right: 10px; border: 1px solid #ddd; border-radius: 4px; }
        ul { list-style: none; padding: 0; }
        li { padding: 8px 0; border-bottom: 1px dashed #eee; display: flex; justify-content: space-between; align-items: center; }
        .rule-card { background: #f9f9f9; padding: 15px; border-radius: 6px; margin-top: 10px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>참여자 상세 정보: ${p.name}</h1>
        <a href="/participants" class="btn" style="background:#6c757d;">← 목록으로 돌아가기</a>

        <div class="section" style="margin-top:20px;">
            <h3>기본 정보</h3>
            <p><strong>입사일:</strong> ${p.joinDate}</p>
            <p><strong>퇴사일:</strong> ${p.leaveDate != null ? p.leaveDate : '재직 중'}</p>
        </div>

        <!-- 1. 불참 기간 관리 -->
        <div class="section">
            <h3>불참 기간 설정 (Range)</h3>
            <p><small>휴가나 출장 등 특정 기간 동안 모든 업무에서 제외됩니다.</small></p>
            <form action="/participants/addRange" method="post" style="margin-bottom:15px;">
                <input type="hidden" name="participantId" value="${p.id}">
                시작: <input type="date" name="startDate" required>
                종료: <input type="date" name="endDate" required>
                <button type="submit" class="btn" style="background:#28a745;">기간 추가</button>
            </form>
            <h4>현재 설정된 기간</h4>
            <ul>
                <c:forEach var="range" items="${p.unavailableRanges}">
                    <li>
                        ${range.startDate} ~ ${range.endDate}
                        <form action="/participants/deleteRange" method="post" style="display:inline;">
                            <input type="hidden" name="id" value="${range.id}">
                            <input type="hidden" name="participantId" value="${p.id}">
                            <button type="submit" class="btn" style="background:#dc3545; padding: 2px 8px; font-size: 0.8em;">삭제</button>
                        </form>
                    </li>
                </c:forEach>
                <c:if test="${empty p.unavailableRanges}"><li>등록된 기간이 없습니다.</li></c:if>
            </ul>
        </div>

        <!-- 2. 가용(참여 가능) 규칙 관리 -->
        <div class="section">
            <h3>가용(근무 가능) 규칙 설정 (Availability Rules)</h3>
            <p><small>참여자가 배정될 수 있는 날짜 패턴을 정의합니다. (규칙이 없으면 모든 날짜 가용)</small></p>
            
            <div class="rule-card">
                <h4>일반 가용 패턴 추가</h4>
                <form action="/participants/addRule" method="post">
                    <input type="hidden" name="participantId" value="${p.id}">
                    패턴: 
                    <select name="ruleType" required>
                        <option value="EVEN_DAYS">기준일로부터 격일(1일째) 근무</option>
                        <option value="ODD_DAYS">기준일로부터 격일(2일째) 근무</option>
                        <option value="WEEKDAYS_ONLY">평일만 근무 (월~금)</option>
                        <option value="WEEKENDS_ONLY">주말만 근무 (토~일)</option>
                    </select>
                    기준일: <input type="date" name="baseDate" value="<%= java.time.LocalDate.now() %>">
                    <button type="submit" class="btn" style="background:#28a745;">패턴 추가</button>
                </form>
            </div>

            <div class="rule-card" style="margin-top:15px; border-top: 2px solid #eee;">
                <h4>N일 주기(교대) 규칙 추가</h4>
                <p><small>특정 기준일부터 N일마다 한 번씩 근무가 가능한 경우 설정합니다.</small></p>
                <form action="/participants/addRule" method="post">
                    <input type="hidden" name="participantId" value="${p.id}">
                    <input type="hidden" name="ruleType" value="N_DAY_CYCLE">
                    기준일: <input type="date" name="baseDate" value="<%= java.time.LocalDate.now() %>" required>
                    주기(일): <input type="number" name="cycleDays" value="3" min="2" style="width:60px;" required>
                    <button type="submit" class="btn" style="background:#007bff;">주기 규칙 추가</button>
                </form>
            </div>

            <h4 style="margin-top:20px;">현재 적용된 가용성 규칙</h4>
            <ul>
                <c:forEach var="rule" items="${p.availabilityRules}">
                    <li>
                        <span>
                            <c:choose>
                                <c:when test="${rule.ruleType == 'EVEN_DAYS'}">
                                    <strong>격일(1일째)</strong> 근무 (기준: ${rule.ruleValue})
                                </c:when>
                                <c:when test="${rule.ruleType == 'ODD_DAYS'}">
                                    <strong>격일(2일째)</strong> 근무 (기준: ${rule.ruleValue})
                                </c:when>
                                <c:when test="${rule.ruleType == 'WEEKDAYS_ONLY'}"><strong>평일</strong>만 근무</c:when>
                                <c:when test="${rule.ruleType == 'WEEKENDS_ONLY'}"><strong>주말</strong>만 근무</c:when>
                                <c:when test="${rule.ruleType == 'N_DAY_CYCLE'}">
                                    <strong>N일 주기:</strong> ${rule.ruleValue} (기준일:주기)
                                </c:when>
                                <c:otherwise>${rule.ruleType}</c:otherwise>
                            </c:choose>
                        </span>
                        <form action="/participants/deleteRule" method="post" style="display:inline;">
                            <input type="hidden" name="id" value="${rule.id}">
                            <input type="hidden" name="participantId" value="${p.id}">
                            <button type="submit" class="btn" style="background:#dc3545; padding: 2px 8px; font-size: 0.8em;">삭제</button>
                        </form>
                    </li>
                </c:forEach>
                <c:if test="${empty p.availabilityRules}"><li>등록된 가용 규칙이 없습니다. (모든 날짜 가용 가능)</li></c:if>
            </ul>
        </div>
    </div>
</body>
</html>
