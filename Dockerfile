# jdk17 Image Start
FROM eclipse-temurin:17-jdk

# 작업 디렉토리 설정
WORKDIR /app

# JSON 키 파일들 복사
COPY src/main/resources/smart-amp-google.json /app/

# 인자 설정 - JAR_File
ARG JAR_FILE=build/libs/*.jar

# jar 파일 복제
COPY ${JAR_FILE} app.jar

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]