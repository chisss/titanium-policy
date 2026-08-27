package com.titanium.policy.infrastructure.generator;

import java.time.LocalDate;

/**
 * 业务编号流水持久化端口。
 * <p>
 * 该接口只属于基础设施实现细节，便于在不改变领域发号契约的前提下替换数据库适配器并进行隔离测试。
 * </p>
 */
interface PolicyNoSequenceStore {

    /**
     * 原子预占一个流水号。
     *
     * @param tenantId 租户ID
     * @param documentType 单据类型
     * @param businessDate 业务日期
     * @return 已预占的流水号
     */
    long next(String tenantId, String documentType, LocalDate businessDate);
}
