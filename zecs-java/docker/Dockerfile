# zECS Java Dockerfile

# 빌드 스테이지
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Maven 캐시 최적화
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 소스 코드 복사 및 빌드
COPY src ./src
RUN mvn clean package -DskipTests -B

# 런타임 스테이지
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 보안: 루트 사용자로 실행하지 않음
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 빌드된 JAR 복사
COPY --from=builder /app/target/zecs-java-1.0.0.jar app.jar

# 컨테이너 포트 (HTTP)
EXPOSE 50100

# 메타데이터
LABEL maintainer="Enterprise Caching System"
LABEL version="1.0.0"
LABEL description="z/OS Enterprise Caching System - Java Implementation"

# 사용자 전환
USER appuser

# JVM 옵션
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:50100/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
