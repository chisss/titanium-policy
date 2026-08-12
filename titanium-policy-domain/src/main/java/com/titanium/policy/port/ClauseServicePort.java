package com.titanium.policy.port;

import java.util.List;

import com.titanium.policy.valueobject.policy.ClauseSnapshot;
import com.titanium.policy.valueobject.policy.CoverageSnapshot;

/**
 * 条款服务端口（driven port，与聚合平级）
 * <p>
 * 出单时经此端口向条款域取「条款元信息 + 保险责任清单」，装配为保单的条款快照（L2.5）与
 * 责任快照（L4）。快照一经签发即冻结，条款域后续改版不影响存量保单。
 * </p>
 * <p>
 * 🔴 <b>强类型化</b>：改造前两个方法返回 {@code Object}/{@code List<?>}，违反「Feign 返回类型
 * 必须强类型、禁 Map/Object」红线，且调用方需自行 JSON 拆包。现出参为领域侧防腐值对象，
 * 条款域 DTO 细节封闭在 infrastructure 的 Adapter 内。
 * </p>
 */
public interface ClauseServicePort {

    /**
     * 取条款快照（条款编码/名称/版本）。
     *
     * @param clauseId     条款ID
     * @param isMainClause 是否作为主条款绑定
     * @param tenantId     租户ID
     * @return 条款快照；条款不存在时返回 null
     */
    ClauseSnapshot fetchClauseSnapshot(String clauseId, boolean isMainClause, String tenantId);

    /**
     * 取条款下的保险责任清单，装配为责任快照。
     * <p>
     * 责任的挂载层级（{@code attachLevel}）与挂载对象（{@code attachRefId}）由调用方（出单装配器）
     * 依标的结构决定——条款域只定义「有哪些责任」，不知道本次投保有几个标的。
     * </p>
     *
     * @param clauseId 条款ID
     * @param tenantId 租户ID
     * @return 责任快照列表（未含挂载信息，待装配器填充）；无责任时返回空列表
     */
    List<CoverageSnapshot> fetchCoverageSnapshots(String clauseId, String tenantId);
}
