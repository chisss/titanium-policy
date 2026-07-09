package com.titanium.policy.web.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.api.dto.CreateProposalDTO;
import com.titanium.policy.api.dto.ProposalDTO;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.web.request.CreateProposalRequest;
import com.titanium.policy.web.response.ProposalVO;

/**
 * 投保意向单 Web 层对象映射器（MapStruct）
 * <p>
 * 把边界输入翻译成 CQRS 命令/查询的转换枢纽：HTTP {@code Request} → 领域命令 {@code CreateProposalCommand}
 * （Controller 用）、远程 {@code DTO} → 领域命令（Provider 用）、读模型结果 → 展示 {@code VO}（Controller 用）、
 * 读模型结果 → 对外 {@code DTO}（Provider 用）。application 门面入参即领域命令，本映射器在 web 层完成
 * Request/DTO → Command 的结构翻译（{@code BigDecimal}+币种 → {@code Money}）。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ProposalWebMapper {

    /**
     * 读模型查询结果 → 响应 VO（Controller 用）
     *
     * @param result 查询结果
     * @return 响应 VO
     */
    ProposalVO toVO(ProposalQueryResult result);

    /**
     * HTTP Request → 领域命令（Controller 用）
     * <p>
     * 租户ID 以请求头透传值为准；意向保额/意向保费与币种组装为 {@code Money}。
     * </p>
     *
     * @param request 创建投保意向单请求
     * @param tenantId 租户ID（请求头）
     * @return 创建投保意向单命令
     */
    @Mapping(target = "intendedSumInsured", expression = "java(toMoney(request.getIntendedSumInsured(), request.getCurrency()))")
    @Mapping(target = "intendedPremium", expression = "java(toMoney(request.getIntendedPremium(), request.getCurrency()))")
    @Mapping(target = "tenantId", source = "tenantId")
    CreateProposalCommand toCommand(CreateProposalRequest request, String tenantId);

    /**
     * 远程 DTO → 领域命令（Provider 用）
     * <p>
     * 意向保额/意向保费与币种组装为 {@code Money}；DTO 未承载的字段留空。
     * </p>
     *
     * @param dto 创建投保意向单 DTO
     * @param tenantId 租户ID（请求头）
     * @return 创建投保意向单命令
     */
    @Mapping(target = "intendedSumInsured", expression = "java(toMoney(dto.getIntendedSumInsured(), dto.getCurrency()))")
    @Mapping(target = "intendedPremium", expression = "java(toMoney(dto.getIntendedPremium(), dto.getCurrency()))")
    @Mapping(target = "tenantId", source = "tenantId")
    CreateProposalCommand toCommand(CreateProposalDTO dto, String tenantId);

    /**
     * 读模型结果 → 对外 DTO（Provider 用）
     * <p>
     * 状态字段由领域枚举 {@code ProposalStatus.StatusCode} 以 {@code .name()} 转为 String，
     * 避免 api 模块依赖 domain 层枚举。
     * </p>
     *
     * @param result 读模型查询结果
     * @return 投保意向单 DTO
     */
    @Mapping(target = "status", expression = "java(result.getStatus() != null ? result.getStatus().name() : null)")
    ProposalDTO toDTO(ProposalQueryResult result);

    /**
     * BigDecimal + 币种 → Money 值对象（空安全，缺省币种 CNY）
     */
    default Money toMoney(BigDecimal value, String currency) {
        return value != null ? Money.of(value, currency != null ? currency : "CNY") : null;
    }
}
