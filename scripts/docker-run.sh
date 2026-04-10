set -e

echo "=== [1/4] Gradle build ==="
./gradlew bootJar

echo "=== [2/4] Docker image build ==="
docker build -t tixy-pt-app .

echo "=== [3/4] 기존 컨테이너 정리 ==="
docker stop tixy-pt-app || true
docker rm tixy-pt-app || true

echo "=== [4/4] Spring 컨테이너 실행 ==="
docker run -d \
  --name tixy-pt-app \
  --env-file .env \
  --network spring-net \
  -p 8081:8081 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql-compose:3306/tixy \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=12345678 \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e SPRING_DATA_REDIS_HOST=redis-compose \
  -v ${HOME}/tixy-pt/uploads:/root/tixy-pt/uploads \
  tixy-pt-app

echo "=== 로그 출력 ==="
docker logs -f tixy-pt-app