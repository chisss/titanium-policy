package com.titanium.policy.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.web.response.ProposalVO;

/**
 * 投保意向单 Web 层对象映射器（MapStruct）
 * <p>
 * 负责读模型查询结果 → 响应 VO 的转换，隔离表现层与读模型。 命令构造由应用服务承担（表现层不依赖领域命令），故本映射器不涉及 Request → Command。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ProposalWebMapper {

    /**
     * 读模型查询结果 → 响应 VO
     *
     * @param result 查询结果
     * @return 响应 VO
     */
    ProposalVO toVO(ProposalQueryResult result);
}
