# jar 파일 빌드
# Gradle 이 들어 있는 공식 이미지로 빌드한다. gradlew 는 시작할 때마다 GitHub Releases 에서 배포판(gradle-8.7-bin.zip)을
# 내려받는데, CloudType 빌드 환경에서는 그 주소가 계속 504 를 돌려줘 재시도로도 못 넘겼다(2026-11-09).
# Docker Hub 이미지는 받아지므로 배포판 다운로드 자체를 없앤다. 버전은 gradle-wrapper.properties(8.7)와 맞춘다.
FROM gradle:8.7-jdk17 AS builder
WORKDIR /workspace

COPY build.gradle .
COPY settings.gradle .
COPY offline-repo offline-repo
COPY src src
RUN gradle bootJar --no-daemon

# jar 실행
FROM eclipse-temurin:17-jre as runtime

COPY --from=builder /workspace/build/libs/*.jar app.jar

ENV PROFILE ${PROFILE}

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=${PROFILE}", "-jar", "/app.jar"]
