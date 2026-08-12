#!/bin/bash

# 测试出单 API 的脚本
# 使用方式：./test-issuance-api.sh

set -e

BASE_URL="http://localhost:8080"
TENANT_ID="TEST_TENANT_001"

echo "========================================="
echo "Titanium Policy Issuance API 测试"
echo "========================================="

# 测试 1: 提交出单请求
echo ""
echo "测试 1: 提交一步出单请求"
echo "-----------------------------------------"

BIZ_NO="TEST_BIZ_$(date +%Y%m%d%H%M%S)"

curl -X POST "${BASE_URL}/api/v1/issuances" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "bizNo": "'${BIZ_NO}'",
    "issuanceStrategy": "MERGE_ONE_POLICY",
    "userId": "USER001",
    "channelId": "CH001",
    "salesChannel": "ONLINE",
    "holder": {
      "name": "张三",
      "certType": "ID_CARD",
      "certNo": "110101199001011234",
      "mobile": "13800138000"
    },
    "insuredList": [{
      "name": "张三",
      "certType": "ID_CARD",
      "certNo": "110101199001011234",
      "age": 30,
      "gender": "MALE"
    }],
    "periodStart": "2026-09-01T00:00:00",
    "periodEnd": "2027-08-31T23:59:59",
    "collectionMode": "ONLINE",
    "planLines": [{
      "lineNo": 1,
      "productId": "PROD001",
      "productCategory": "MAIN",
      "sumInsured": 500000.00,
      "coveragePeriodValue": 1,
      "coveragePeriodUnit": "YEAR",
      "paymentFrequency": "ANNUAL"
    }],
    "currency": "CNY"
  }' | jq .

echo ""
echo "-----------------------------------------"

# 测试 2: 查询出单进度
echo ""
echo "测试 2: 查询出单进度"
echo "-----------------------------------------"

curl -X GET "${BASE_URL}/api/v1/issuances/${BIZ_NO}" \
  -H "X-Tenant-Id: ${TENANT_ID}" | jq .

echo ""
echo "-----------------------------------------"

echo ""
echo "========================================="
echo "测试完成"
echo "========================================="
