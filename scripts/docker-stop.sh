set -e

echo "=== Spring 컨테이너 종료 ==="
docker stop tixy-pt-app || true

echo "=== 컨테이너 제거 ==="
docker rm tixy-pt-app || true

echo "=== 종료 완료 — 컨테이너 상태 ==="
docker ps -a