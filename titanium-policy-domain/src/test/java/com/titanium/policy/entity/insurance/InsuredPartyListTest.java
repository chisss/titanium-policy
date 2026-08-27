package com.titanium.policy.entity.insurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.policy.entity.insurance.InsuredPartyList.BeneficiaryInfo;
import com.titanium.policy.entity.insurance.InsuredPartyList.HolderInfo;
import com.titanium.policy.entity.insurance.InsuredPartyList.InsuredInfo;

/**
 * 投保参与方清单·受益份额校验测试（P1.4 受益人份额=100% 不变量）
 * <p>
 * 验证 {@code verifyPartyInfo}/{@code isBeneficiaryRatioValid} 的份额守恒规则：
 * 无受益人默认法定继承不校验；有受益人则各比例为正且总和为 100%。
 * </p>
 */
class InsuredPartyListTest {

    private HolderInfo holder() {
        return new HolderInfo("C-H-1", "H-1", "张三", IdCardType.CHINA_ID_CARD, "3301**********1234", "13800000000");
    }

    private InsuredInfo insured() {
        return new InsuredInfo("C-I-1", "I-1", "张三", IdCardType.CHINA_ID_CARD, "3301**********1234", 30,
                CustomerGender.MALE, null);
    }

    private BeneficiaryInfo beneficiary(String id, double ratio) {
        return new BeneficiaryInfo("C-" + id, id, "受益人" + id, IdCardType.CHINA_ID_CARD, "3301**********5678",
                BeneficiaryType.DEATH, 1, ratio);
    }

    private InsuredPartyList partyList(List<BeneficiaryInfo> beneficiaries) {
        return new InsuredPartyList("L-1", holder(), List.of(insured()), beneficiaries);
    }

    @Test
    @DisplayName("受益份额之和为100%校验通过")
    void shouldPassWhenRatioSumsToOne() {
        InsuredPartyList list = partyList(List.of(beneficiary("B-1", 0.6d), beneficiary("B-2", 0.4d)));
        if (!list.verifyPartyInfo()) {
            throw new AssertionError("份额之和为100%应校验通过");
        }
    }

    @Test
    @DisplayName("受益份额之和不足100%校验失败")
    void shouldFailWhenRatioSumsBelowOne() {
        InsuredPartyList list = partyList(List.of(beneficiary("B-1", 0.5d), beneficiary("B-2", 0.4d)));
        if (list.verifyPartyInfo()) {
            throw new AssertionError("份额之和不足100%应校验失败");
        }
    }

    @Test
    @DisplayName("受益份额之和超过100%校验失败")
    void shouldFailWhenRatioSumsAboveOne() {
        InsuredPartyList list = partyList(List.of(beneficiary("B-1", 0.7d), beneficiary("B-2", 0.5d)));
        if (list.verifyPartyInfo()) {
            throw new AssertionError("份额之和超过100%应校验失败");
        }
    }

    @Test
    @DisplayName("受益比例非正数校验失败")
    void shouldFailWhenRatioNonPositive() {
        InsuredPartyList list = partyList(List.of(beneficiary("B-1", 1.0d), beneficiary("B-2", 0d)));
        if (list.verifyPartyInfo()) {
            throw new AssertionError("存在非正受益比例应校验失败");
        }
    }

    @Test
    @DisplayName("无受益人视为法定继承，不校验份额")
    void shouldPassWhenNoBeneficiary() {
        InsuredPartyList list = partyList(List.of());
        if (!list.verifyPartyInfo()) {
            throw new AssertionError("无受益人应默认通过（法定继承）");
        }
    }

    @Test
    @DisplayName("浮点份额三等分（各1/3）总和视为100%通过")
    void shouldPassWhenThreeEqualShares() {
        double third = 1.0d / 3.0d;
        InsuredPartyList list = partyList(
                List.of(beneficiary("B-1", third), beneficiary("B-2", third), beneficiary("B-3", third)));
        if (!list.verifyPartyInfo()) {
            throw new AssertionError("三等分份额总和应视为100%通过（容差）");
        }
    }

    @Test
    @DisplayName("历史受益人事件缺少性别和手机号时仍可回放")
    void shouldDeserializeLegacyBeneficiarySnapshotWithoutNewFields() throws Exception {
        String legacyJson = """
                {
                  "customerId": "C-B-1",
                  "beneficiaryId": "B-1",
                  "name": "李四",
                  "certType": null,
                  "certNo": "3301**********5678",
                  "beneficiaryType": null,
                  "order": 1,
                  "beneficiaryRatio": 1.0
                }
                """;

        BeneficiaryInfo beneficiary = new ObjectMapper().readValue(legacyJson, BeneficiaryInfo.class);

        assertEquals("C-B-1", beneficiary.customerId());
        assertNull(beneficiary.gender());
        assertNull(beneficiary.phone());
    }
}
