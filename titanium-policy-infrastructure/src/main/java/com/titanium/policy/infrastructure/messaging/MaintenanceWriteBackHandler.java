package com.titanium.policy.infrastructure.messaging;

/**
 * 保全回写处理器（策略接口）
 * <p>
 * 每种保全类型对应一个处理器，将保全执行翻译为具体的保单命令并下发。
 * 以多态策略替代类型分支 switch，新增保全类型时新增实现即可（开闭原则）。
 * </p>
 */
public interface MaintenanceWriteBackHandler {

    /**
     * 本处理器负责的保全类型编码（对应 maintenance 域 MaintenanceType 枚举名）
     *
     * @return 保全类型编码
     */
    String supportedType();

    /**
     * 将保全执行翻译为保单命令并下发
     *
     * @param context 保全回写上下文
     */
    void handle(MaintenanceWriteBackContext context);
}
