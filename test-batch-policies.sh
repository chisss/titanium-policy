#!/usr/bin/env bash

# 批量出单 API 测试。数据库投影请使用 verify-projection.sh 单独核对。

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-TEST-TENANT-001}"
PRODUCT_ID="${PRODUCT_ID:-PRD-TEST-LIFE-001}"
EXPECTED_ISSUANCE_MODE="${EXPECTED_ISSUANCE_MODE:-THREE_STEP}"
ISSUANCE_PATH="${ISSUANCE_PATH:-/api/v1/issuances}"
CURL="${CURL:-curl}"
ID_TYPE="CHINA_ID_CARD"
COUNT="${1:-10}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-15}"
POLL_INTERVAL="${POLL_INTERVAL:-2}"
CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-5}"
REQUEST_TIMEOUT="${REQUEST_TIMEOUT:-30}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

[ "$#" -le 1 ] || { echo "只接受一个数量参数" >&2; exit 1; }

normalize_uint_in_range() {
  local name="$1"
  local value="$2"
  local minimum="$3"
  local maximum="$4"

  case "${value}" in
    ''|*[!0-9]*)
      echo "${name} 必须是 ${minimum}..${maximum} 的整数" >&2
      return 1
      ;;
  esac
  while [ "${#value}" -gt 1 ] && [ "${value#0}" != "${value}" ]; do
    value="${value#0}"
  done
  if [ "${#value}" -gt "${#maximum}" ] \
      || [ "${value}" -lt "${minimum}" ] \
      || [ "${value}" -gt "${maximum}" ]; then
    echo "${name} 必须是 ${minimum}..${maximum} 的整数" >&2
    return 1
  fi
  printf '%s' "${value}"
}

COUNT="$(normalize_uint_in_range COUNT "${COUNT}" 1 50)"
POLL_ATTEMPTS="$(normalize_uint_in_range POLL_ATTEMPTS "${POLL_ATTEMPTS}" 1 60)"
POLL_INTERVAL="$(normalize_uint_in_range POLL_INTERVAL "${POLL_INTERVAL}" 1 60)"
CONNECT_TIMEOUT="$(normalize_uint_in_range CONNECT_TIMEOUT "${CONNECT_TIMEOUT}" 1 30)"
REQUEST_TIMEOUT="$(normalize_uint_in_range REQUEST_TIMEOUT "${REQUEST_TIMEOUT}" 1 120)"
if [ "${CONNECT_TIMEOUT}" -gt "${REQUEST_TIMEOUT}" ]; then
  echo "CONNECT_TIMEOUT 不能大于 REQUEST_TIMEOUT" >&2
  exit 1
fi

command -v "${CURL}" >/dev/null 2>&1 || { echo "缺少命令: ${CURL}" >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "缺少命令: jq" >&2; exit 1; }
command -v mktemp >/dev/null 2>&1 || { echo "缺少命令: mktemp" >&2; exit 1; }

# 仅使用当前有效的行政区代码，并把生日限制在每月 1..28 日以避免非法日期。
ADMIN_CODES=(110101 120101 310101 320102 330106 420106 440106 500103 510104 610102)
CURRENT_YEAR="$(date +%Y)"
CURRENT_MONTH_DAY="$(date +%m%d)"

generate_china_id() {
  local administrative_code="$1"
  local birth_date="$2"
  local sequence_code="$3"
  local base="${administrative_code}${birth_date}$(printf '%03d' "${sequence_code}")"
  local weights=(7 9 10 5 8 4 2 1 6 3 7 9 10 5 8 4 2)
  local checks=(1 0 X 9 8 7 6 5 4 3 2)
  local sum=0
  local index

  for index in "${!weights[@]}"; do
    sum=$((sum + ${base:index:1} * weights[index]))
  done
  printf '%s%s' "${base}" "${checks[sum % 11]}"
}

prepare_identity() {
  local seed="$1"
  local gender_code="$2"
  local age="$3"
  local admin_count="${#ADMIN_CODES[@]}"
  local sequence_slots=499
  local mixed
  local sequence_index
  local sequence_code
  local day
  local month
  local administrative_index
  local birth_month_day
  local birth_year

  if [ "${gender_code}" = "MALE" ]; then
    sequence_slots=500
  fi
  mixed=$((seed % (admin_count * 12 * 28 * sequence_slots)))
  sequence_index=$((mixed % sequence_slots))
  mixed=$((mixed / sequence_slots))
  day=$((mixed % 28 + 1))
  mixed=$((mixed / 28))
  month=$((mixed % 12 + 1))
  mixed=$((mixed / 12))
  administrative_index=$((mixed % admin_count))

  if [ "${gender_code}" = "MALE" ]; then
    sequence_code=$((sequence_index * 2 + 1))
  else
    sequence_code=$((sequence_index * 2 + 2))
  fi
  printf -v birth_month_day '%02d%02d' "${month}" "${day}"
  birth_year=$((10#${CURRENT_YEAR} - age))
  if [ $((10#${birth_month_day})) -gt $((10#${CURRENT_MONTH_DAY})) ]; then
    birth_year=$((birth_year - 1))
  fi
  BIRTH_DATE="${birth_year}${birth_month_day}"
  CERT_NO="$(generate_china_id "${ADMIN_CODES[administrative_index]}" "${BIRTH_DATE}" "${sequence_code}")"
}

HTTP_BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/titanium-batch-issuance.XXXXXX")"
HTTP_BODY=""
HTTP_STATUS=""
cleanup() {
  rm -f "${HTTP_BODY_FILE}"
}
trap cleanup EXIT HUP INT TERM

request_json() {
  local method="$1"
  local url="$2"
  local payload="${3-}"
  local curl_status
  local curl_args=(
    --silent --show-error --retry 2 --retry-delay 1 --retry-max-time "${REQUEST_TIMEOUT}"
    --connect-timeout "${CONNECT_TIMEOUT}" --max-time "${REQUEST_TIMEOUT}"
    --request "${method}" "${url}"
    --header "X-Tenant-Id: ${TENANT_ID}"
  )

  if [ -n "${payload}" ]; then
    curl_args+=(--header "Content-Type: application/json" --data "${payload}")
  fi
  : > "${HTTP_BODY_FILE}"
  set +e
  HTTP_STATUS="$("${CURL}" "${curl_args[@]}" --output "${HTTP_BODY_FILE}" --write-out '%{http_code}')"
  curl_status=$?
  set -e
  HTTP_BODY="$(<"${HTTP_BODY_FILE}")"

  if [ "${curl_status}" -ne 0 ]; then
    echo "HTTP 请求失败: method=${method}, curlStatus=${curl_status}, body=${HTTP_BODY:-<empty>}" >&2
    return 1
  fi
  case "${HTTP_STATUS}" in
    2??) ;;
    *)
      echo "HTTP 状态异常: method=${method}, status=${HTTP_STATUS}, body=${HTTP_BODY:-<empty>}" >&2
      return 1
      ;;
  esac
  if ! printf '%s' "${HTTP_BODY}" | jq -e 'type == "object"' >/dev/null 2>&1; then
    echo "响应不是 JSON 对象: method=${method}, status=${HTTP_STATUS}, body=${HTTP_BODY:-<empty>}" >&2
    return 1
  fi
}

api_error_message() {
  printf '%s' "$1" | jq -r '
    if type != "object" then "响应根节点不是对象"
    else (if (.data | type) == "object" then .data.rejectReason else empty end)
      // .message
      // .error
      // "未知错误"
    end
  ' 2>/dev/null || printf '%s' '响应无法解析'
}

read_progress_stage() {
  local body="$1"
  if ! printf '%s' "${body}" | jq -e '
      type == "object"
      and .code == "00000000"
      and (.data | type) == "object"
      and (.data.currentStage | type) == "string"
      and (.data.currentStage | length) > 0
    ' >/dev/null 2>&1; then
    echo "进度响应契约无效: $(api_error_message "${body}")，body=${body}" >&2
    return 1
  fi
  PROGRESS_STAGE="$(printf '%s' "${body}" | jq -r '.data.currentStage')"
  case "${PROGRESS_STAGE}" in
    ACCEPTED|VALIDATING|QUOTING|PROPOSAL_CREATED|INSURANCE_CREATED|UNDERWRITING|PENDING_COLLECTION|POLICY_ISSUED|POLICY_EFFECTIVE|REJECTED) ;;
    *)
      echo "进度响应包含未知阶段: ${PROGRESS_STAGE}" >&2
      return 1
      ;;
  esac
}

issuance_output_ready() {
  local body="$1"
  local stage="$2"

  case "${stage}" in
    POLICY_ISSUED|POLICY_EFFECTIVE)
      printf '%s' "${body}" | jq -e '
        (.data.policies | type) == "array"
        and (.data.policies | length) > 0
        and (.data.policies[0].policyId | type) == "string"
        and (.data.policies[0].policyId | length) > 0
      ' >/dev/null 2>&1
      ;;
    PENDING_COLLECTION)
      printf '%s' "${body}" | jq -e '
        (.data.policies | type) == "array"
        and (.data.policies | length) > 0
        and (.data.policies[0].policyId | type) == "string"
        and (.data.policies[0].policyId | length) > 0
        and (.data.billId | type) == "string"
        and (.data.billId | length) > 0
        and (.data.paymentOrderId | type) == "string"
        and (.data.paymentOrderId | length) > 0
      ' >/dev/null 2>&1
      ;;
    *)
      return 1
      ;;
  esac
}

echo "========================================="
echo "批量出单 API 测试"
echo "租户: ${TENANT_ID}"
echo "产品: ${PRODUCT_ID}"
echo "数量: ${COUNT}"
echo "========================================="

SUCCESS=0
FAILED=0
RUN_SEED=$((10#$(date +%s) + $$ * 1000003 + RANDOM * 1009 + RANDOM))

for ((i = 1; i <= COUNT; i++)); do
  BIZ_NO="BIZ_TEST_$(date +%Y%m%d%H%M%S)_$$_${i}_${RANDOM}"
  AGE=$((20 + i % 40))
  if [ $((i % 2)) -eq 0 ]; then
    GENDER="MALE"
  else
    GENDER="FEMALE"
  fi
  IDENTITY_SEED=$((RUN_SEED + i * 7919))
  prepare_identity "${IDENTITY_SEED}" "${GENDER}" "${AGE}"
  MOBILE="138$(printf '%08d' "$((IDENTITY_SEED % 100000000))")"
  # COUNT 最大为 50，因此测试保额最大 260 万，低于测试产品 500 万上限。
  SUM_INSURED=$((100000 + i * 50000))

  REQUEST_PAYLOAD="$(jq -n \
    --arg bizNo "${BIZ_NO}" \
    --arg userId "USER$(printf '%03d' "${i}")" \
    --arg holderName "测试投保人${i}" \
    --arg productId "${PRODUCT_ID}" \
    --arg idType "${ID_TYPE}" \
    --arg idNo "${CERT_NO}" \
    --arg mobile "${MOBILE}" \
    --arg gender "${GENDER}" \
    --argjson age "${AGE}" \
    --argjson sumInsured "${SUM_INSURED}" \
    '{
      bizNo: $bizNo,
      issuanceStrategy: "MERGE_ONE_POLICY",
      userId: $userId,
      channelId: "CH001",
      salesChannel: "ONLINE",
      holder: {
        name: $holderName,
        certType: $idType,
        certNo: $idNo,
        mobile: $mobile
      },
      insuredList: [{
        name: $holderName,
        certType: $idType,
        certNo: $idNo,
        age: $age,
        gender: $gender,
        mobile: $mobile,
        relationToHolder: "SELF"
      }],
      periodStart: "2026-09-01T00:00:00",
      periodEnd: "2046-09-01T00:00:00",
      collectionMode: "ONLINE",
      planLines: [{
        lineNo: 1,
        productId: $productId,
        productCategory: "MAIN",
        sumInsured: $sumInsured,
        coveragePeriodValue: 20,
        coveragePeriodUnit: "YEAR",
        paymentFrequency: "ANNUAL",
        premiumPaymentYears: 20
      }],
      currency: "CNY"
    }')"

  echo ""
  echo "[${i}/${COUNT}] 创建出单请求 ${BIZ_NO}..."

  if ! BIZ_NO="${BIZ_NO}" \
      TENANT_ID="${TENANT_ID}" \
      EXPECTED_ISSUANCE_MODE="${EXPECTED_ISSUANCE_MODE}" \
      VERIFY_PHASE=PRE_SUBMIT \
      bash "${PROJECT_ROOT}/verify-projection.sh"; then
    FAILED=$((FAILED + 1))
    continue
  fi

  if ! request_json POST "${BASE_URL}${ISSUANCE_PATH}" "${REQUEST_PAYLOAD}"; then
    FAILED=$((FAILED + 1))
    continue
  fi
  RESPONSE="${HTTP_BODY}"
  if ! printf '%s' "${RESPONSE}" | jq -e '
      .code == "00000000" and (.data | type) == "object" and .data.success == true
    ' >/dev/null 2>&1; then
    echo "  失败: $(api_error_message "${RESPONSE}")，body=${RESPONSE}" >&2
    FAILED=$((FAILED + 1))
    continue
  fi

  FINAL_STAGE=""
  POLL_FAILED=false
  OUTPUT_READY=false
  for ((attempt = 1; attempt <= POLL_ATTEMPTS; attempt++)); do
    if ! request_json GET "${BASE_URL}${ISSUANCE_PATH}/${BIZ_NO}"; then
      POLL_FAILED=true
      break
    fi
    PROGRESS="${HTTP_BODY}"
    if ! read_progress_stage "${PROGRESS}"; then
      POLL_FAILED=true
      break
    fi
    FINAL_STAGE="${PROGRESS_STAGE}"
    if issuance_output_ready "${PROGRESS}" "${FINAL_STAGE}"; then
      OUTPUT_READY=true
      break
    fi
    if [ "${FINAL_STAGE}" = "REJECTED" ]; then
      echo "  异步出单失败: $(api_error_message "${PROGRESS}")" >&2
      break
    fi
    if [ "${attempt}" -lt "${POLL_ATTEMPTS}" ]; then
      sleep "${POLL_INTERVAL}"
    fi
  done

  if [ "${POLL_FAILED}" = false ] && [ "${OUTPUT_READY}" = true ]; then
    echo "  成功: ${FINAL_STAGE}"
    SUCCESS=$((SUCCESS + 1))
  else
    if [ "${FINAL_STAGE}" = "PENDING_COLLECTION" ]; then
      echo "  失败: 待收费阶段缺少保单、账单或支付单关联" >&2
    else
      echo "  失败: 异步终态 ${FINAL_STAGE:-UNKNOWN}" >&2
    fi
    FAILED=$((FAILED + 1))
  fi
  sleep 1
done

echo ""
echo "========================================="
echo "批量创建完成"
echo "成功: ${SUCCESS}"
echo "失败: ${FAILED}"
echo "========================================="

if [ "${FAILED}" -gt 0 ]; then
  exit 1
fi
