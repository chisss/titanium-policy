#!/bin/bash

# 批量创建保单测试数据
# 使用当前登录用户的租户ID: 1

set -e

BASE_URL="http://localhost:8080"
TENANT_ID="1"  # admin用户的租户
COUNT=${1:-10}  # 默认创建10条，可通过参数指定

echo "========================================="
echo "批量创建保单测试数据"
echo "租户: ${TENANT_ID}"
echo "数量: ${COUNT}"
echo "========================================="

SUCCESS=0
FAILED=0

for i in $(seq 1 $COUNT); do
  BIZ_NO="BIZ_TENANT1_$(date +%Y%m%d%H%M%S)_${i}"

  echo ""
  echo "[$i/$COUNT] 创建保单 ${BIZ_NO}..."

  RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/issuances" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    -d '{
      "bizNo": "'${BIZ_NO}'",
      "issuanceStrategy": "MERGE_ONE_POLICY",
      "userId": "USER'$(printf "%03d" $i)'",
      "channelId": "CH001",
      "salesChannel": "ONLINE",
      "holder": {
        "name": "测试投保人'${i}'",
        "certType": "ID_CARD",
        "certNo": "110101'$(date +%Y%m%d | cut -c3-)'$(printf "%04d" $i)'",
        "mobile": "138'$(printf "%08d" $i)'"
      },
      "insuredList": [{
        "name": "测试被保人'${i}'",
        "certType": "ID_CARD",
        "certNo": "110101'$(date +%Y%m%d | cut -c3-)'$(printf "%04d" $i)'",
        "age": '$((20 + i % 50))',
        "gender": "'$([ $((i % 2)) -eq 0 ] && echo "MALE" || echo "FEMALE")'"
      }],
      "periodStart": "2026-09-01T00:00:00",
      "periodEnd": "'$((2026 + i % 10))'-08-31T23:59:59",
      "collectionMode": "ONLINE",
      "planLines": [{
        "lineNo": 1,
        "productId": "PROD001",
        "productCategory": "MAIN",
        "sumInsured": '$((100000 + i * 50000))',
        "coveragePeriodValue": '$((1 + i % 5))',
        "coveragePeriodUnit": "YEAR",
        "paymentFrequency": "ANNUAL"
      }],
      "currency": "CNY"
    }')

  if echo "$RESPONSE" | jq -e '.code == 200 or .code == "00000000"' > /dev/null 2>&1; then
    echo "  ✅ 成功"
    ((SUCCESS++))
  else
    echo "  ❌ 失败: $(echo $RESPONSE | jq -r '.message // .error // .')"
    ((FAILED++))
  fi

  # 避免过快请求导致业务号冲突
  sleep 0.5
done

echo ""
echo "========================================="
echo "批量创建完成"
echo "成功: ${SUCCESS}"
echo "失败: ${FAILED}"
echo "========================================="

# 等待投影完成
echo ""
echo "等待 5 秒让投影处理器消费事件..."
sleep 5

echo ""
echo "验证投影结果:"
docker exec titanium-mysql mysql -uroot -proot policy_db -e "
  SELECT '租户1保单数' label, COUNT(*) count FROM t_policy_view WHERE tenant_id='${TENANT_ID}'
  UNION ALL
  SELECT '租户TEST-TENANT-001保单数', COUNT(*) FROM t_policy_view WHERE tenant_id='TEST-TENANT-001';
" 2>&1 | grep -v "Using a password"

echo ""
echo "========================================="
echo "测试完成！前端现在应该能看到 ${SUCCESS} 条保单了"
echo "========================================="
