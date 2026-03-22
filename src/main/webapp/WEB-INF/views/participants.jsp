<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>참여자 관리 - 공정 일정 스케줄러</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 800px; margin: auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        h1 { color: #333; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }
        th { background-color: #f8f9fa; }
        .form-group { margin-bottom: 15px; }
        label { display: block; margin-bottom: 5px; }
        input { width: 100%; padding: 8px; box-sizing: border-box; }
        .btn { padding: 10px 15px; background: #333; color: #fff; border: none; border-radius: 5px; cursor: pointer; text-decoration: none; }
    </style>
</head>
<body>
    <div class="container">
        <h1>참여자 관리</h1>
        <a href="/" class="btn">달력으로 돌아가기</a>

        <h2 style="margin-top:30px;">새 참여자 등록</h2>
        <form action="/participants/add" method="post">
            <div class="form-group">
                <label>이름:</label>
                <input type="text" name="name" required placeholder="예: 홍길동">
            </div>
            <div class="form-group">
                <label>입사일/참여시작일:</label>
                <input type="date" name="joinDate" required value="2024-03-01">
            </div>
            <button type="submit" class="btn" style="background:#28a745;">등록하기</button>
        </form>

        <h2 style="margin-top:30px;">등록된 참여자 목록</h2>
        <table>
            <thead>
                <tr>
                    <th>ID</th><th>이름</th><th>참여시작일</th><th>관리</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="p" items="${participants}">
                    <tr>
                        <td>${p.id}</td>
                        <td><a href="/participants/detail?id=${p.id}" style="color:#007bff; font-weight:bold;">${p.name}</a></td>
                        <td>${p.joinDate}</td>
                        <td>
                            <form action="/participants/delete" method="post" onsubmit="return confirm('정말 삭제하시겠습니까? (관련된 모든 일정도 함께 관리됩니다.)');">
                                <input type="hidden" name="participantId" value="${p.id}">
                                <button type="submit" class="btn" style="background:#dc3545; padding:5px 10px;">삭제</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
</body>
</html>
