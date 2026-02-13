package com.titanium.policy.service;

import org.springframework.stereotype.Service;

import com.titanium.policy.valueobject.RiskAssessmentStep;

import lombok.extern.slf4j.Slf4j;

/**
 * 风控校验执行器
 * <p>
 * 执行各风控校验步骤，调用对应外部服务或本地规则。
 * </p>
 */
@Slf4j
@Service
public class RiskAssessmentExecutor {

    /**
     * 执行风控校验步骤
     *
     * @param step 风控步骤
     * @param request 出单请求
     * @return 是否通过
     */
    public boolean execute(RiskAssessmentStep step, IssuanceRequest request) {
        log.info("执行风控校验步骤: {}", step.getName());

        return switch (step) {
            case BLACKLIST_CHECK -> executeBlacklistCheck(request);
            case DUPLICATE_CHECK -> executeDuplicateCheck(request);
            case BASIC_UNDERWRITING -> executeBasicUnderwriting(request);
            case MANUAL_UNDERWRITING -> {
                // 人工核保不在此处同步执行，返回 true 表示允许继续流程
                log.info("人工核保步骤，标记为待核保");
                yield true;
            }
            case AML_CHECK -> executeAmlCheck(request);
            case HEALTH_DECLARATION_CHECK -> executeHealthDeclarationCheck(request);
        };
    }

    /**
     * 黑名单校验 - 调用客户域反欺诈服务
     */
    private boolean executeBlacklistCheck(IssuanceRequest request) {
        // TODO: 调用客户域 anti-fraud service
        log.info("黑名单校验通过, customerId={}", request.policyHolderId());
        return true;
    }

    /**
     * 重复投保校验 - 查询是否已有同期同产品保单
     */
    private boolean executeDuplicateCheck(IssuanceRequest request) {
        // TODO: 查询保单表判断重复投保
        log.info("重复投保校验通过, productCode={}", request.productCode());
        return true;
    }

    /**
     * 基础自动核保 - 调用核保规则引擎
     */
    private boolean executeBasicUnderwriting(IssuanceRequest request) {
        // TODO: 调用核保域规则引擎
        log.info("基础自动核保通过");
        return true;
    }

    /**
     * 反洗钱校验
     */
    private boolean executeAmlCheck(IssuanceRequest request) {
        // TODO: 调用反洗钱服务
        log.info("反洗钱校验通过");
        return true;
    }

    /**
     * 健康告知校验
     */
    private boolean executeHealthDeclarationCheck(IssuanceRequest request) {
        // TODO: 调用健康告知校验
        log.info("健康告知校验通过");
        return true;
    }
}
