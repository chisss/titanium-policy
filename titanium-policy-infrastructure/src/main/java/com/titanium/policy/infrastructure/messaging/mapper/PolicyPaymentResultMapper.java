package com.titanium.policy.infrastructure.messaging.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.infrastructure.messaging.inbound.PaymentOrderPaidMessage;
import com.titanium.policy.valueobject.payment.PremiumCollectionNotice;

/**
 * 支付出账成功消息 → 保费实收回写通知装配（MapStruct）
 * <p>
 * 归属 infrastructure：本装配器吃的是**入站防腐消息**（{@link PaymentOrderPaidMessage}），
 * 属跨域传输结构，故与监听器同层，不得上提到 application（否则内层编译期依赖基础设施，
 * 由 ArchUnit {@code applicationShouldNotDependOnInfrastructure} 硬性拦截）。
 * application 收到的仍是干净的领域值对象。
 * </p>
 * <p>
 * {@code businessId} → {@code policyId} 是跨域语义转译：对端只认「业务单号」，本域据
 * {@code businessType=POLICY} 过滤后即知其必为保单ID。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface PolicyPaymentResultMapper {

    /**
     * 支付成功消息 → 保费实收回写通知。
     *
     * @param message 支付出账成功防腐入站消息
     * @return 保费实收回写通知（金额已按币种构造为值对象）
     */
    @Mapping(target = "policyId", source = "message.businessId")
    @Mapping(target = "paymentId", source = "message.paymentId")
    @Mapping(target = "paymentNo", source = "message.paymentNo")
    @Mapping(target = "collectedAmount", expression = "java(toMoney(message.amount(), message.currency()))")
    @Mapping(target = "collectedAt", source = "message.paidAt")
    @Mapping(target = "tenantId", source = "message.tenantId")
    PremiumCollectionNotice toNotice(PaymentOrderPaidMessage message);

    /**
     * 金额 + 币种 code → 金额值对象（空安全）。
     * <p>
     * 币种缺失或无法识别时回落人民币：本字段随 m2-903 才加入出站载荷，此前发布并滞留在主题中的
     * 在途消息没有该字段，回落可让这些消息正常回写而非被丢弃。本域保费收取以人民币为主，
     * 与该回落一致。
     * </p>
     */
    @Named("paymentMoney")
    default Money toMoney(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return null;
        }
        CurrencyEnum currency = CurrencyEnum.fromCode(currencyCode);
        return Money.of(amount, currency != null ? currency.getCode() : CurrencyEnum.CNY.getCode());
    }
}
