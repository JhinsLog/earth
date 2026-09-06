# 이미 빌드된 jar을 담기만 하는 얇은 이미지.
# 홈서버가 2코어라 Gradle 빌드를 서버에서 돌리면 매우 느리다. jar은 개발 PC나 CI에서 만들어 온다:
#   ./gradlew bootJar   →   build/libs/earth-backend-*.jar
FROM eclipse-temurin:25-jre-alpine

# root로 돌리지 않는다. 컨테이너가 뚫렸을 때 호스트로 넘어갈 여지를 줄인다.
RUN addgroup -S earth && adduser -S -G earth earth

WORKDIR /app
COPY --chown=earth:earth build/libs/earth-backend-*.jar app.jar

USER earth
EXPOSE 8080

# 컨테이너 메모리 제한을 JVM이 인식하게 한다.
# 이걸 안 걸면 JVM이 "호스트 전체 메모리"를 기준으로 힙을 잡아서,
# compose의 mem_limit을 넘는 순간 OOM Killer에 조용히 죽는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# exec을 붙여야 java가 PID 1이 되어 docker stop의 SIGTERM을 직접 받는다.
# 없으면 셸이 신호를 삼켜서 10초 뒤 SIGKILL로 강제 종료된다(정상 종료 훅이 안 돈다).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
