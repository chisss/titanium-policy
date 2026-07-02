package com.titanium.policy.application.orchestration;

/**
 * 保全回写策略（应用层编排策略接口）
 * <p>
 * 每种保全类型对应一个策略实现，将保全执行翻译为具体的保单命令并下发（发命令属应用编排职责）。
 * 以多态策略替代类型分支 switch，新增保全类型时新增实现即可（开闭原则）。
 * </p>
 * <p>
 * 命名用 {@code Strategy} 而非 Handler：本接口的实现是「事件驱动的命令编排者」，
 * 归属 application 层，按规约不得用与 Axon 消息处理器撞名的 Handler/Processor 后缀。
 * </p>
 */
public interface MaintenanceWriteBackStrategy {

    /**
     * 本策略负责的保全类型编码（对应 maintenance 域 MaintenanceType 枚举名）
     *
     * @return 保全类型编码
     */
    String supportedType();

    /**
     * 将保全执行翻译为保单命令并下发
     *
     * @param context 保全回写上下文
     */
    void writeBack(MaintenanceWriteBackContext context);
}
