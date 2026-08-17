#!/usr/bin/env bash
# MeterForge Burst Traffic Simulation Script (Bash / curl)
# Demonstrates atomic multi-policy rate limiting against local Gateway (:8890).

COUNT=${1:-10}
GATEWAY_URL=${2:-"http://localhost:8890/v1/forecast/tokyo"}
API_KEY=${3:-"mf_dev_nsdemo123456_seedednorthstardemosecretkey9999"}

DISPLAY_KEY="${API_KEY:0:20}..."

echo "============================================================"
echo "  MeterForge Burst Traffic Dispatcher"
echo "  Target: $GATEWAY_URL"
echo "  Count:  $COUNT concurrent requests"
echo "  Key:    $DISPLAY_KEY"
echo "============================================================"

TMP_DIR=$(mktemp -d)

for i in $(seq 1 "$COUNT"); do
  (
    RESP=$(curl -s -o /dev/null -w "%{http_code} %{time_total}" -H "X-API-Key: $API_KEY" "$GATEWAY_URL")
    HTTP_CODE=$(echo "$RESP" | awk '{print $1}')
    TIME_SEC=$(echo "$RESP" | awk '{print $2}')
    LATENCY=$(awk "BEGIN {printf \"%.0f\", $TIME_SEC * 1000}")
    echo "$i,$HTTP_CODE,$LATENCY" > "$TMP_DIR/result_$i.txt"
  ) &
done

wait

echo ""
echo "--- Execution Summary ---"
ALLOWED=0
LIMITED=0
OTHER=0

for i in $(seq 1 "$COUNT"); do
  if [ -f "$TMP_DIR/result_$i.txt" ]; then
    IFS=',' read -r IDX CODE LAT < "$TMP_DIR/result_$i.txt"
    echo "  Request #$IDX: HTTP $CODE - ${LAT}ms"
    if [ "$CODE" -eq 200 ]; then
      ALLOWED=$((ALLOWED + 1))
    elif [ "$CODE" -eq 429 ]; then
      LIMITED=$((LIMITED + 1))
    else
      OTHER=$((OTHER + 1))
    fi
  fi
done

rm -rf "$TMP_DIR"

echo "============================================================"
echo "  Total Sent:     $COUNT"
echo "  Allowed (200):  $ALLOWED"
echo "  Limited (429):  $LIMITED"
echo "  Other Errors:   $OTHER"
echo "============================================================"
