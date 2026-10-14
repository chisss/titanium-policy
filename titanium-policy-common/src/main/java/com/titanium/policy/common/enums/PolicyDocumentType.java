package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/**
 * 保单单证类型枚举
 * <p>
 * 标识保单文档（PolicyDocument）的单证业务分类，为保单域内部约定，metadata 无对应枚举，
 * 故归属本模块 domain 层。
 * </p>
 */
@Getter
public enum PolicyDocumentType implements BaseEnum {
    /** 电子保单 */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    E_POLICY(1, "E_POLICY", "电子保单", "电子保单单证"),
    /** 纸质保单 */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    PAPER_POLICY(2, "PAPER_POLICY", "纸质保单", "纸质保单单证"),
    /** 投保单 */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    PROPOSAL(3, "PROPOSAL", "投保单", "投保单单证"),
    /** 批单 */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    ENDORSEMENT(4, "ENDORSEMENT", "批单", "保全批单单证"),
    /** 发票 */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    INVOICE(5, "INVOICE", "发票", "保费发票单证");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    PolicyDocumentType(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 根据编码反查枚举（统一范式入口，委托 {@link BaseEnum}）
     *
     * @param code 单证类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PolicyDocumentType fromCode(String code) {
        return BaseEnum.fromCode(PolicyDocumentType.class, code);
    }
}
