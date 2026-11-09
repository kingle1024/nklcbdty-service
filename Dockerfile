# jar 파일 빌드
FROM eclipse-temurin:17-jdk AS builder

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY offline-repo offline-repo
COPY src src
RUN chmod +x ./gradlew
# Gradle 배포판 다운로드가 504·타임아웃으로 실패하는 일이 잦다(2026-11-09 열 번 넘게).
# 코드와 무관한 네트워크 실패라 배포판만 먼저 받으며 몇 번 다시 시도한다.
RUN for i in 1 2 3 4 5 6; do ./gradlew --version && exit 0; echo "Gradle 다운로드 재시도 $i/6"; sleep 20; done; exit 1
RUN ./gradlew bootjar

# jar 실행
FROM eclipse-temurin:17-jre as runtime

COPY --from=builder build/libs/*.jar app.jar

ENV PROFILE ${PROFILE}

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=${PROFILE}", "-jar", "/app.jar"]
