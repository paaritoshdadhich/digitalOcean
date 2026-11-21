#!/usr/bin/env bash
# End-to-end demo against a running flag-management-system (default :8080).
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8080}"
ACTOR="${ACTOR:-leadership-demo}"
FLAG="${FLAG:-new_checkout}"
USER_A="${USER_A:-user-alice}"
USER_B="${USER_B:-user-bob}"

hdr=(-H "Content-Type: application/json" -H "X-Actor-Id: ${ACTOR}")

echo "== Health =="
curl -sf "${BASE}/actuator/health" | tee /tmp/ff-health.json
echo

echo "== Create flag (global OFF) =="
curl -sf -X POST "${BASE}/api/v1/flags" "${hdr[@]}" \
  -d "{\"name\":\"${FLAG}\",\"description\":\"Leadership demo flag\",\"enabled\":false}" | tee /tmp/ff-create.json
echo

echo "== Evaluate ${USER_A} (expect GLOBAL false) =="
curl -sf -X POST "${BASE}/api/v1/evaluate" -H "Content-Type: application/json" \
  -d "{\"flagName\":\"${FLAG}\",\"userId\":\"${USER_A}\"}" | tee /tmp/ff-eval1.json
echo

echo "== Enable global =="
curl -sf -X PUT "${BASE}/api/v1/flags/${FLAG}" "${hdr[@]}" \
  -d "{\"description\":\"Leadership demo flag\",\"enabled\":true}" | tee /tmp/ff-update.json
echo

echo "== Evaluate ${USER_A} (expect GLOBAL true) =="
curl -sf -X POST "${BASE}/api/v1/evaluate" -H "Content-Type: application/json" \
  -d "{\"flagName\":\"${FLAG}\",\"userId\":\"${USER_A}\"}" | tee /tmp/ff-eval2.json
echo

echo "== Override ${USER_B} to false =="
curl -sf -X PUT "${BASE}/api/v1/flags/${FLAG}/overrides/${USER_B}" "${hdr[@]}" \
  -d '{"enabled":false}' | tee /tmp/ff-override.json
echo

echo "== Evaluate ${USER_B} (expect USER_OVERRIDE false) =="
curl -sf -X POST "${BASE}/api/v1/evaluate" -H "Content-Type: application/json" \
  -d "{\"flagName\":\"${FLAG}\",\"userId\":\"${USER_B}\"}" | tee /tmp/ff-eval3.json
echo

echo "== Evaluate ${USER_A} still GLOBAL true =="
curl -sf -X POST "${BASE}/api/v1/evaluate" -H "Content-Type: application/json" \
  -d "{\"flagName\":\"${FLAG}\",\"userId\":\"${USER_A}\"}" | tee /tmp/ff-eval4.json
echo

echo "== Delete override for ${USER_B} =="
curl -sf -o /dev/null -w "%{http_code}\n" -X DELETE "${BASE}/api/v1/flags/${FLAG}/overrides/${USER_B}" \
  -H "X-Actor-Id: ${ACTOR}"

echo "== Evaluate ${USER_B} after delete (expect GLOBAL true) =="
curl -sf -X POST "${BASE}/api/v1/evaluate" -H "Content-Type: application/json" \
  -d "{\"flagName\":\"${FLAG}\",\"userId\":\"${USER_B}\"}" | tee /tmp/ff-eval5.json
echo

echo "== List flags =="
curl -sf "${BASE}/api/v1/flags" | tee /tmp/ff-list.json
echo
echo "Demo complete."
