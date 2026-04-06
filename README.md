# 📅 Scheduler Management System

Java 21과 Spring Boot 3.4를 기반으로 구축된 지능형 스케줄 자동 배정 시스템입니다. 참가자별 가용 시간, 작업 정의, 그리고 제약 조건을 고려하여 최적의 스케줄을 생성하고 관리합니다.

## 🚀 주요 기능

### 1. 스케줄 배정 및 관리
- **자동 배정 엔진 (Distribution Engine)**: 정의된 규칙과 참가자의 상태를 분석하여 스케줄을 자동으로 배정합니다.
- **캘린더 뷰**: 배정된 스케줄을 캘린더 형식으로 확인하고 관리할 수 있습니다.

### 2. 참가자 관리 (Participant Management)
- 참가자 등록 및 상세 정보 관리
- **가용성 규칙 (Availability Rules)**: 참가자별로 가능한 시간대 설정
- **제외 기간 (Unavailable Ranges)**: 휴가, 출장 등 특정 기간 배정 제외 설정

### 3. 작업 정의 (Task Definition)
- 수행해야 할 작업의 종류와 속성 정의
- 작업별 우선순위 및 제약 조건 설정

### 4. 통계 및 데이터 관리
- **참가자 통계**: 배정 횟수, 작업 분포 등 통계 데이터 제공
- **데이터 이관 (Migration)**: 데이터 내보내기 및 가져오기 기능 지원

## 🛠 기술 스택

- **Backend**: Java 21, Spring Boot 3.4.3
- **Database**: H2 Database (In-Memory/File)
- **Persistence**: Spring Data JPA (Hibernate)
- **Frontend**: JSP (Jakarta Standard Tag Library - JSTL)
- **Build Tool**: Gradle
- **Lombok**: 보일러플레이트 코드 제거 및 생산성 향상

## 🏗 프로젝트 구조

```text
src/main/java/org/example/scheduler/
├── component/      # 공통 컴포넌트
├── controller/     # 웹 요청 처리 (MVC Controllers)
├── domain/         # JPA 엔티티 및 도메인 모델
├── dto/            # 데이터 전송 객체
├── repository/     # Spring Data JPA 리포지토리
└── service/        # 비즈니스 로직 및 배정 엔진
```

## 🏃 시작하기

### 요구 사항
- JDK 21 이상
- Gradle 8.x 이상

### 실행 방법
```bash
./gradlew bootRun
```
실행 후 브라우저에서 `http://localhost:8080`에 접속합니다.

### 테스트
```bash
./gradlew test
```

## 📄 라이선스
이 프로젝트는 MIT 라이선스를 따릅니다.
