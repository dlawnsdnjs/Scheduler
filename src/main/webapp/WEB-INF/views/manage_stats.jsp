<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>데이터 백업 및 복구 - 공정 일정 스케줄러</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 800px; margin: auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        textarea { width: 100%; height: 300px; font-family: monospace; margin-bottom: 20px; }
        .btn { padding: 10px 15px; background: #333; color: #fff; border: none; border-radius: 5px; cursor: pointer; text-decoration: none; }
        .section { margin-bottom: 40px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>데이터 백업 및 복구</h1>
        <p>참여자들의 업무 참여 횟수와 마지막 참여일 데이터를 관리합니다. 이를 통해 월간 연속성을 유지할 수 있습니다.</p>
        <a href="/" class="btn">달력으로 돌아가기</a>

        <div class="section" style="margin-top:30px;">
            <h2>데이터 내보내기 (Export)</h2>
            <p>아래 버튼을 클릭하여 현재 데이터를 JSON 형식으로 확인하고 다른 곳에 저장하세요.</p>
            <button onclick="fetchExportData()" class="btn" style="background:#007bff;">데이터 생성하기</button>
            <textarea id="exportArea" readonly placeholder="데이터를 생성하면 여기에 나타납니다."></textarea>
        </div>

        <div class="section">
            <h2>데이터 가져오기 (Import)</h2>
            <p>저장해둔 JSON 데이터를 아래에 붙여넣어 참여자들의 통계 정보를 복구하거나 갱신하세요.</p>
            <form action="/stats/import" method="post">
                <textarea name="json" required placeholder='[{"name": "강백호", "taskTotalCounts": {...}, "taskLastAssignedDates": {...}}, ...]'></textarea>
                <button type="submit" class="btn" style="background:#28a745;" onclick="return confirm('기존 통계 데이터가 덮어씌워집니다. 계속하시겠습니까?');">데이터 적용하기</button>
            </form>
        </div>
    </div>

    <script>
        function fetchExportData() {
            fetch('/stats/export')
                .then(response => response.text())
                .then(data => {
                    document.getElementById('exportArea').value = data;
                });
        }
    </script>
</body>
</html>
