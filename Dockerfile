# jar 파일 빌드
FROM eclipse-temurin:17-jdk AS builder

# GitHub Packages (nklcbdty-common) 다운로드용 인증. multi-stage 라 runtime 에는 남지 않음.
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x ./gradlew
RUN ./gradlew bootjar

# jar 실행
FROM eclipse-temurin:17-jre as runtime

COPY --from=builder build/libs/*.jar app.jar

ENV PROFILE ${PROFILE}

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=${PROFILE}", "-jar", "/app.jar"]
