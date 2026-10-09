package com.titanium.policy.api.request.maintenance;

import com.titanium.metadata.enums.policy.PolicyServiceRestrictionAction;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 服务受限类保全项（挂失/解除挂失）随请求递达保单域的受限事实。
 * <p>
 * 与「结构化字段变更」「金额事实」「合同状态动作」<b>并列</b>的第四种生效载荷。挂失这类保全项既无字段
 * 目录入口、也无金额、也不改合同状态（挂失保单仍是 {@code EFFECTIVE}），变更的实质就是这条受限标记的
 * 施加或解除。
 * </p>
 * <p>
 * 🔴 <b>载荷种类互斥</b>：本载荷不得与字段变更、金额事实或状态动作同时提交——它只推进「服务受限」
 * 这条与保单状态正交的维度，混装即语义不明。保单域在 {@code Policy.validateNewMaintenanceRequest}
 * 侧二次把关。
 * </p>
 * <p>
 * 🔴 <b>不含生效时间</b>：受限生效时点恒取本次保全应用的 {@code effectiveAt}，另立字段会造出两处可漂移
 * 的时点，并让请求摘要多算一个同值字段。
 * </p>
 *
 * @param type   受限种类（当前仅挂失 {@code LOSS_REPORT}）
 * @param action 受限动作（{@code APPLY} 施加 / {@code RELEASE} 解除）
 */
@Schema(description = "保全服务受限事实")
public record PolicyServiceRestrictionRequest(
        @Schema(description = "受限种类", example = "LOSS_REPORT")
        @NotNull PolicyServiceRestrictionType type,

        @Schema(description = "受限动作（APPLY 施加 / RELEASE 解除）", example = "APPLY")
        @NotNull PolicyServiceRestrictionAction action) {
}
