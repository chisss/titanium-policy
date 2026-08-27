package com.titanium.policy.generator;

/**
 * 保单域业务编号发号端口。
 * <p>
 * 编号分配属于需要持久化原子性的技术能力，领域层只依赖本端口，不保存进程内流水状态。
 * </p>
 */
public interface PolicyNoGenerator {

    /**
     * 生成保单号。
     *
     * @param tenantId 租户ID
     * @return 保单号，例如 {@code POL202608130000001}
     */
    String generatePolicyNo(String tenantId);

    /**
     * 生成投保单号。
     *
     * @param tenantId 租户ID
     * @return 投保单号，例如 {@code INS202608130000001}
     */
    String generateInsuranceNo(String tenantId);

    /**
     * 生成意向单号。
     *
     * @param tenantId 租户ID
     * @return 意向单号，例如 {@code PRP202608130000001}
     */
    String generateProposalNo(String tenantId);
}
