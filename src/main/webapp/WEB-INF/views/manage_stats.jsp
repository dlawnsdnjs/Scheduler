<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>데이터 백업 및 복구 - 공정 일정 스케줄러</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 800px; margin: auto; background: #fff; padding: 30px; border-radius: 12px; box-shadow: 0 0 15px rgba(0,0,0,0.1); }
        .btn { padding: 12px 20px; background: #333; color: #fff; border: none; border-radius: 6px; cursor: pointer; text-decoration: none; font-weight: bold; }
        .section { margin-bottom: 50px; padding: 20px; border: 1px solid #eee; border-radius: 8px; }
        .form-group { margin-bottom: 15px; }
        label { display: block; margin-bottom: 5px; font-weight: bold; }
        input[type="date"], input[type="file"] { width: 100%; padding: 10px; box-sizing: border-box; border: 1px solid #ccc; border-radius: 4px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>데이터 백업 및 복구</h1>
        <p>참여자 통계와 실제 배정 일정을 포함한 전체 데이터를 파일로 내보내거나 가져옵니다.</p>
        <a href="/" class="btn" style="background:#6c757d;">← 달력으로 돌아가기</a>

        <div class="section" style="margin-top:30px;">
            <h2>1. 데이터 내보내기 (Export to File)</h2>
            <p>지정한 기간의 배정 정보와 현재 참여자 통계가 포함된 JSON 파일을 다운로드합니다.</p>
            <form action="/stats/export" method="get">
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
                <button type="submit" class="btn" style="background:#007bff; width:100%;">백업 파일 다운로드 (.json)</button>
            </form>
        </div>

        <div class="section">
            <h2>2. 데이터 가져오기 (Import from File)</h2>
            <p>저장해둔 백업 파일을 선택하여 데이터를 복구합니다. (기존 데이터와 병합됩니다.)</p>
            <form action="/stats/import" method="post" enctype="multipart/form-data">
                <div class="form-group">
                    <label>백업 파일 선택:</label>
                    <input type="file" name="file" accept=".json,.txt" required>
                </div>
                <button type="submit" class="btn" style="background:#28a745; width:100%;" onclick="return confirm('파일의 데이터를 시스템에 적용하시겠습니까?');">데이터 복구 실행</button>
            </form>
        </div>
    </div>
</body>
</html>
