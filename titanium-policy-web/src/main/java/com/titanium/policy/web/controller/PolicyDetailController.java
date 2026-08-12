package com.titanium.policy.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;
import com.titanium.policy.application.query.PolicyLineAppQueryService;
import com.titanium.policy.query.result.PolicyCoverageQueryResult;
import com.titanium.policy.query.result.PolicyFullDetailQueryResult;
import com.titanium.policy.query.result.PolicyProductQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicySubjectQueryResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单详情控制器（一单多险读侧，面向后台）
 * <p>
 * 提供保单构成的分层查询能力：全景（一次拿全）、险种段、保险责任、标的、按客户角色查保单。
 * 与 {@code PolicyController}（保单生命周期操作）分立——本控制器只读，不含任何命令。
 * </p>
 */
@Slf4j
@Tag(name = "保单详情查询", description = "一单多险读侧：全景/险种段/责任/标的/按客户角色")
@RestController
@RequestMapping("/web/v1/policies")
@RequiredArgsConstructor
public class PolicyDetailController {

    private final PolicyLineAppQueryService policyLineAppQueryService;

    /**
     * 保单全景查询
     * <p>
     * 一次返回：产品（险种）+ 条款（含版本）+ 保险责任（保额/免赔/赔付比例/等待期）+ 标的 +
     * 投保人/被保险人/受益人（含受益顺位与份额）+ 缴费计划 + 收费方式与实收金额 + 保障期间 +
     * 等待期与犹豫期届满日 + 电子保单标记 + 渠道。
     * </p>
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单全景；保单不存在时 404
     */
    @Operation(summary = "保单全景查询", description = "一次返回保单全部维度，后台详情页专用")
    @GetMapping("/{policyId}/full-detail")
    public ResponseEntity<PolicyFullDetailQueryResult> getFullDetail(@PathVariable("policyId") String policyId,
                                                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return policyLineAppQueryService.findFullDetail(policyId, tenantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 查询保单险种段清单
     * <p>
     * 一单多险时返回多段（主险 + 附加险），每段含独立的保额、保费、保障期间、缴费条件、
     * 核保结论与承保状态。供保全按段操作、佣金与再保按段拆分口径使用。
     * </p>
     *
     * @param policyId    保单ID
     * @param withDetails 是否装配段内条款/标的/责任明细（缺省 false，减少查询开销）
     * @param tenantId    租户ID
     * @return 险种段列表
     */
    @Operation(summary = "查询保单险种段", description = "一单多险的段清单，可选是否装配段内明细")
    @GetMapping("/{policyId}/lines")
    public ResponseEntity<List<PolicyProductQueryResult>> getLines(
            @PathVariable("policyId") String policyId,
            @RequestParam(value = "withDetails", defaultValue = "false") boolean withDetails,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(policyLineAppQueryService.findLines(policyId, withDetails, tenantId));
    }

    /**
     * 查询保单保险责任清单
     * <p>
     * 理赔域定责依据：责任保额、免赔、赔付比例、责任级等待期，均为出单时点冻结的快照。
     * </p>
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 责任列表
     */
    @Operation(summary = "查询保单保险责任", description = "理赔定责依据，含免赔与赔付比例")
    @GetMapping("/{policyId}/coverages")
    public ResponseEntity<List<PolicyCoverageQueryResult>> getCoverages(@PathVariable("policyId") String policyId,
                                                                       @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(policyLineAppQueryService.findCoverages(policyId, tenantId));
    }

    /**
     * 查询保单标的清单
     * <p>
     * 车险多车、企财险多分项时返回多个标的，各含类型化属性包（车辆 VIN/初登日期、
     * 厂房建筑结构/消防等级等）。
     * </p>
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 标的列表
     */
    @Operation(summary = "查询保单标的", description = "含各险种特有的类型化属性包")
    @GetMapping("/{policyId}/subjects")
    public ResponseEntity<List<PolicySubjectQueryResult>> getSubjects(@PathVariable("policyId") String policyId,
                                                                     @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(policyLineAppQueryService.findSubjects(policyId, tenantId));
    }

    /**
     * 按客户与保险角色查询其名下保单
     * <p>
     * 同一客户在不同保单中可能是投保人、被保险人或受益人，三种角色下「我的保单」含义不同。
     * 角色参数为空时返回三者并集（按保单去重）。
     * </p>
     *
     * @param customerId 客户ID
     * @param role       保险角色码（POLICY_HOLDER/INSURED/BENEFICIARY；可空）
     * @param page       页码（0 起）
     * @param size       每页条数
     * @param tenantId   租户ID
     * @return 保单列表
     */
    @Operation(summary = "按客户角色查保单", description = "支持投保人/被保险人/受益人三种角色维度")
    @GetMapping("/by-customer/{customerId}")
    public ResponseEntity<List<PolicyQueryResult>> getByCustomerRole(
            @PathVariable("customerId") String customerId,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        InsuranceRole insuranceRole = role != null ? InsuranceRole.fromCode(role) : null;
        return ResponseEntity
                .ok(policyLineAppQueryService.findByCustomerRole(customerId, insuranceRole, tenantId, page, size));
    }
}
