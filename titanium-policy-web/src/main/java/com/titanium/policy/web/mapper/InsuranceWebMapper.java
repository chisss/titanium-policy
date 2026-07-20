package com.titanium.policy.web.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.api.request.ConvertToInsuranceRequest;
import com.titanium.policy.api.response.InsuranceResponse;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.web.dto.ConvertToInsuranceDTO;
import com.titanium.policy.web.response.InsuranceVO;

/**
 * 投保单 Web 层对象映射器（MapStruct）
 * <p>
 * 把边界输入翻译成 CQRS 命令/查询的转换枢纽：HTTP {@code Request} → 领域命令
 * {@code ConvertProposalToInsuranceCommand}（Controller 用）、远程 {@code DTO} → 领域命令（Provider 用）、
 * 读模型结果 → 展示 {@code VO}（Controller 用）、读模型结果 → 对外 {@code DTO}（Provider 用）。
 * application 门面入参即领域命令，本映射器在 web 层完成 Request/DTO → Command 的结构翻译
 * （{@code BigDecimal}+币种 → {@code Money}）。命令的业务完整性由聚合根保证。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface InsuranceWebMapper {

    /**
     * HTTP Request → 领域命令（Controller 用）
     * <p>
     * 租户ID 以请求头透传值为准；精确保费 + 币种组装为 {@code Money}（币种字段仅用于组装，不单独入命令）。
     * </p>
     *
     * @param request 转换请求
     * @param tenantId 租户ID（请求头）
     * @return 意向单转投保单命令
     */
    @Mapping(target = "exactPremium", expression = "java(toMoney(request.getExactPremium(), request.getCurrency()))")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "insuredPartyList", ignore = true)
    ConvertProposalToInsuranceCommand toCommand(ConvertToInsuranceDTO request, String tenantId);

    /**
     * 远程 DTO → 领域命令（Provider 用）
     *
     * @param dto 转换 DTO
     * @param tenantId 租户ID（请求头）
     * @return 意向单转投保单命令
     */
    @Mapping(target = "exactPremium", expression = "java(toMoney(dto.getExactPremium(), dto.getCurrency()))")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "insuredPartyList", ignore = true)
    ConvertProposalToInsuranceCommand toCommand(ConvertToInsuranceRequest dto, String tenantId);

    /**
     * 读模型结果 → 展示 VO（Controller 用）
     *
     * @param result 查询结果
     * @return 响应 VO
     */
    InsuranceVO toVO(InsuranceQueryResult result);

    /**
     * 读模型结果 → 对外 DTO（Provider 用）
     * <p>
     * 投保单状态在读模型为 domain 值对象 {@code InsuranceStatus.StatusCode}，api 侧以 {@code String}
     * 承载，故经 {@code .name()} 转换（空安全）。
     * </p>
     *
     * @param result 查询结果
     * @return 投保单 DTO
     */
    @Mapping(target = "status", expression = "java(result.getStatus() != null ? result.getStatus().name() : null)")
    InsuranceResponse toResponse(InsuranceQueryResult result);

    /**
     * BigDecimal + 币种 → Money 值对象（空安全，缺省币种 CNY）
     *
     * @param value 金额
     * @param currency 币种
     * @return Money 值对象
     */
    default Money toMoney(BigDecimal value, String currency) {
        return value != null ? Money.of(value, currency != null ? currency : "CNY") : null;
    }
}
