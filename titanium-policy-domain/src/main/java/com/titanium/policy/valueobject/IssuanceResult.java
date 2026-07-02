package com.titanium.policy.valueobject;

import com.titanium.metadata.enums.product.ProductEnum;

/**
 * 出单结果
 */
public record IssuanceResult(
        boolean success,
        String rejectReason,
        ProductEnum.IssuanceMode issuanceMode,
        String policyId,
        String policyNo,
        String insuranceId,
        String insuranceNo,
        String proposalId,
        String proposalNo
) {
    public static IssuanceResult rejected(String reason) {
        return new IssuanceResult(false, reason, null, null, null, null, null, null, null);
    }

    public static IssuanceResult success(ProductEnum.IssuanceMode mode, String policyId, String policyNo,
                                         String insuranceId, String insuranceNo) {
        return new IssuanceResult(true, null, mode, policyId, policyNo, insuranceId, insuranceNo, null, null);
    }

    public IssuanceResult withProposal(String proposalId, String proposalNo) {
        return new IssuanceResult(this.success, this.rejectReason, this.issuanceMode,
                this.policyId, this.policyNo, this.insuranceId, this.insuranceNo, proposalId, proposalNo);
    }
}
