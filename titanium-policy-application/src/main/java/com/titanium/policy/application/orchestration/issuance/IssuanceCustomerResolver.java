package com.titanium.policy.application.orchestration.issuance;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.policy.application.exception.CustomerResolutionException;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.port.CustomerServicePort;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单参与方防腐解析器。
 * <p>
 * 负责把 HTTP 层只带身份快照的参与方解析为 customer 域真实客户ID，并重建不可变出单请求。解析
 * 发生在产品资格校验之前，保证后续投保单、保单和标的只保存真实客户引用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssuanceCustomerResolver {

    private static final String CUSTOMER_IDENTITY_REQUIRED = "ISSUANCE_CUSTOMER_IDENTITY_REQUIRED";
    private static final String CUSTOMER_NOT_FOUND = "ISSUANCE_CUSTOMER_NOT_FOUND";
    private static final String CUSTOMER_IDENTITY_MISMATCH = "ISSUANCE_CUSTOMER_IDENTITY_MISMATCH";
    private static final String CUSTOMER_RESOLUTION_FAILED = "ISSUANCE_CUSTOMER_RESOLUTION_FAILED";

    private final CustomerServicePort customerServicePort;

    /**
     * 解析请求中所有参与方，并回填人身标的客户ID。
     *
     * @param request 原始出单请求
     * @return 已完成客户ID解析的新请求
     */
    public IssuanceRequest resolve(IssuanceRequest request) {
        if (request == null || request.insuredPartyList() == null) {
            throw new CustomerResolutionException(CUSTOMER_IDENTITY_REQUIRED, "出单参与方清单不能为空");
        }

        Map<IdentityKey, String> resolvedByIdentity = new HashMap<>();
        Map<String, CustomerIdentitySnapshot> identityByCustomerId = new HashMap<>();
        Map<String, String> resolvedByOriginalId = new HashMap<>();
        Map<String, List<String>> resolvedByName = new HashMap<>();
        String operatorId = request.userId() != null ? request.userId() : "issuance";

        InsuredPartyList parties = request.insuredPartyList();
        CustomerGender holderGender = matchingInsuredGender(parties.holderInfo(), parties.insuredList());
        InsuredPartyList.HolderInfo holder = resolveHolder(parties.holderInfo(), request.tenantId(), holderGender,
                operatorId, resolvedByIdentity, identityByCustomerId, resolvedByOriginalId, resolvedByName);

        List<InsuredPartyList.InsuredInfo> insured = new ArrayList<>();
        if (parties.insuredList() != null) {
            for (InsuredPartyList.InsuredInfo item : parties.insuredList()) {
                insured.add(resolveInsured(item, holder, request.tenantId(), operatorId, resolvedByIdentity,
                        identityByCustomerId, resolvedByOriginalId, resolvedByName));
            }
        }

        List<InsuredPartyList.BeneficiaryInfo> beneficiaries = new ArrayList<>();
        if (parties.beneficiaryList() != null) {
            for (InsuredPartyList.BeneficiaryInfo item : parties.beneficiaryList()) {
                beneficiaries.add(resolveBeneficiary(item, request.tenantId(), operatorId, resolvedByIdentity,
                        identityByCustomerId, resolvedByOriginalId, resolvedByName));
            }
        }

        InsuredPartyList resolvedParties = new InsuredPartyList(parties.listId(), holder, List.copyOf(insured),
                List.copyOf(beneficiaries));
        List<IssuancePlanLine> resolvedLines = resolveSubjects(request.planLines(), resolvedParties,
                resolvedByOriginalId, resolvedByName);
        return new IssuanceRequest(request.bizNo(), request.tenantId(), request.userId(), request.marketPackageId(),
                request.issuanceStrategy(), holder != null ? holder.customerId() : null, resolvedParties,
                request.policyForm(), request.insuranceType(), request.periodStart(), request.periodEnd(),
                request.collectionMode(), request.channelId(), request.salesChannel(), request.agentId(), resolvedLines,
                request.quotedPremium(), request.extendData());
    }

    private InsuredPartyList.HolderInfo resolveHolder(InsuredPartyList.HolderInfo holder, String tenantId,
                                                       CustomerGender gender, String operatorId,
                                                       Map<IdentityKey, String> byIdentity,
                                                       Map<String, CustomerIdentitySnapshot> identityByCustomerId,
                                                       Map<String, String> byOriginalId,
                                                       Map<String, List<String>> byName) {
        if (holder == null) {
            throw missingIdentity("投保人");
        }
        String customerId = resolveCustomer(holder.customerId(), holder.name(), holder.certType(), holder.certNo(),
                gender, holder.phone(), tenantId, operatorId, byIdentity, identityByCustomerId);
        remember(holder.customerId(), customerId, holder.name(), byOriginalId, byName);
        return new InsuredPartyList.HolderInfo(customerId, holder.holderId(), holder.name(), holder.certType(),
                holder.certNo(), holder.phone());
    }

    private InsuredPartyList.InsuredInfo resolveInsured(InsuredPartyList.InsuredInfo insured,
                                                        InsuredPartyList.HolderInfo holder, String tenantId,
                                                        String operatorId, Map<IdentityKey, String> byIdentity,
                                                        Map<String, CustomerIdentitySnapshot> identityByCustomerId,
                                                        Map<String, String> byOriginalId,
                                                        Map<String, List<String>> byName) {
        if (insured == null) {
            throw missingIdentity("被保险人");
        }
        String customerId;
        if (isSelf(insured)) {
            customerId = resolveSelfInsured(insured, holder, identityByCustomerId);
        } else {
            customerId = resolveCustomer(insured.customerId(), insured.name(), insured.certType(), insured.certNo(),
                    insured.gender(), insured.phone(), tenantId, operatorId, byIdentity, identityByCustomerId);
        }
        remember(insured.customerId(), customerId, insured.name(), byOriginalId, byName);
        return new InsuredPartyList.InsuredInfo(customerId, insured.insuredId(), insured.name(), insured.certType(),
                insured.certNo(), insured.age(), insured.gender(), insured.phone(), insured.relationToHolder(),
                insured.familyRelation());
    }

    private InsuredPartyList.BeneficiaryInfo resolveBeneficiary(InsuredPartyList.BeneficiaryInfo beneficiary,
                                                                 String tenantId, String operatorId,
                                                                 Map<IdentityKey, String> byIdentity,
                                                                 Map<String, CustomerIdentitySnapshot> identityByCustomerId,
                                                                 Map<String, String> byOriginalId,
                                                                 Map<String, List<String>> byName) {
        if (beneficiary == null) {
            throw missingIdentity("受益人");
        }
        String customerId = resolveCustomer(beneficiary.customerId(), beneficiary.name(), beneficiary.certType(),
                beneficiary.certNo(), beneficiary.gender(), beneficiary.phone(), tenantId, operatorId, byIdentity,
                identityByCustomerId);
        remember(beneficiary.customerId(), customerId, beneficiary.name(), byOriginalId, byName);
        return new InsuredPartyList.BeneficiaryInfo(customerId, beneficiary.beneficiaryId(), beneficiary.name(),
                beneficiary.certType(), beneficiary.certNo(), beneficiary.gender(), beneficiary.phone(),
                beneficiary.beneficiaryType(), beneficiary.order(), beneficiary.beneficiaryRatio());
    }

    /**
     * 自保场景通常只在被保险人输入中携带性别，投保人和被保险人随后会复用同一客户ID。
     * 在首次解析投保人前合并该字段，避免客户主数据以 UNKNOWN 建档后永久缺失性别。
     */
    private CustomerGender matchingInsuredGender(InsuredPartyList.HolderInfo holder,
                                                  List<InsuredPartyList.InsuredInfo> insuredList) {
        if (holder == null || holder.certType() == null || holder.certNo() == null || insuredList == null) {
            return null;
        }
        String holderIdNo = normalize(holder.certNo());
        for (InsuredPartyList.InsuredInfo insured : insuredList) {
            if (insured != null && insured.certType() == holder.certType()
                    && insured.certNo() != null && holderIdNo.equals(normalize(insured.certNo()))
                    && insured.gender() != null) {
                return insured.gender();
            }
        }
        return null;
    }

    private String resolveCustomer(String existingId, String name, IdCardType idType, String idNo,
                                   com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender gender,
                                   String phone, String tenantId, String operatorId,
                                   Map<IdentityKey, String> byIdentity,
                                   Map<String, CustomerIdentitySnapshot> identityByCustomerId) {
        if (existingId != null && !existingId.isBlank()) {
            String customerId = existingId.strip();
            try {
                CustomerIdentitySnapshot actual = customerServicePort.findCustomerIdentity(customerId, tenantId)
                        .orElseThrow(() -> new CustomerResolutionException(CUSTOMER_NOT_FOUND,
                                "客户不存在或不属于当前租户: " + customerId));
                validateIdentity(customerId, name, idType, idNo, actual);
                rememberIdentity(customerId, actual, byIdentity, identityByCustomerId);
                return customerId;
            } catch (CustomerResolutionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.warn("客户存在性查询失败: tenantId={}, customerId={}", tenantId, customerId, exception);
                throw new CustomerResolutionException(CUSTOMER_RESOLUTION_FAILED,
                        "客户主数据服务不可用，无法校验出单参与方", exception, true);
            }
        }
        if (name == null || name.isBlank() || idType == null || idNo == null || idNo.isBlank()) {
            throw missingIdentity(name != null ? name : "参与方");
        }
        IdentityKey key = new IdentityKey(idType, normalize(idNo));
        String cached = byIdentity.get(key);
        if (cached != null) {
            return cached;
        }
        CustomerIdentitySnapshot snapshot = new CustomerIdentitySnapshot(name.strip(), idType, key.idNo(), gender,
                phone, operatorId);
        try {
            String customerId = customerServicePort.resolveCustomer(snapshot, tenantId);
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalStateException("客户域返回空客户ID");
            }
            byIdentity.put(key, customerId);
            identityByCustomerId.put(customerId, snapshot);
            return customerId;
        } catch (CustomerResolutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("客户解析失败: tenantId={}, idType={}, idNo={}", tenantId, idType, mask(key.idNo()), exception);
            throw new CustomerResolutionException(CUSTOMER_RESOLUTION_FAILED,
                    "客户主数据服务不可用，无法完成出单参与方建档", exception, true);
        }
    }

    private String resolveSelfInsured(InsuredPartyList.InsuredInfo insured, InsuredPartyList.HolderInfo holder,
                                      Map<String, CustomerIdentitySnapshot> identityByCustomerId) {
        if (holder == null || holder.customerId() == null || holder.customerId().isBlank()) {
            throw missingIdentity("投保人");
        }
        String holderCustomerId = holder.customerId();
        if (insured.customerId() != null && !insured.customerId().isBlank()
                && !holderCustomerId.equals(insured.customerId().strip())) {
            throw identityMismatch("SELF 被保险人的客户ID与投保人不一致");
        }
        CustomerIdentitySnapshot holderIdentity = identityByCustomerId.get(holderCustomerId);
        if (holderIdentity != null) {
            validateIdentity(holderCustomerId, insured.name(), insured.certType(), insured.certNo(), holderIdentity);
        }
        return holderCustomerId;
    }

    private void validateIdentity(String customerId, String name, IdCardType idType, String idNo,
                                  CustomerIdentitySnapshot actual) {
        if (hasText(name) && (!hasText(actual.fullName()) || !normalize(name).equals(normalize(actual.fullName())))) {
            throw identityMismatch("客户ID与姓名快照不一致: " + customerId);
        }
        if (idType != null && idType != actual.idType()) {
            throw identityMismatch("客户ID与证件类型快照不一致: " + customerId);
        }
        if (hasText(idNo) && (!hasText(actual.idNo()) || !normalize(idNo).equals(normalize(actual.idNo())))) {
            throw identityMismatch("客户ID与证件号码快照不一致: " + customerId);
        }
    }

    private void rememberIdentity(String customerId, CustomerIdentitySnapshot identity,
                                  Map<IdentityKey, String> byIdentity,
                                  Map<String, CustomerIdentitySnapshot> identityByCustomerId) {
        identityByCustomerId.put(customerId, identity);
        if (identity.idType() != null && hasText(identity.idNo())) {
            byIdentity.put(new IdentityKey(identity.idType(), normalize(identity.idNo())), customerId);
        }
    }

    private boolean isSelf(InsuredPartyList.InsuredInfo insured) {
        return hasText(insured.relationToHolder()) && "SELF".equalsIgnoreCase(insured.relationToHolder().strip());
    }

    private List<IssuancePlanLine> resolveSubjects(List<IssuancePlanLine> lines, InsuredPartyList parties,
                                                    Map<String, String> byOriginalId,
                                                    Map<String, List<String>> byName) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<IssuancePlanLine> result = new ArrayList<>();
        for (IssuancePlanLine line : lines) {
            List<IssuancePlanLine.SubjectIntent> subjects = line.subjects();
            if (subjects == null || subjects.isEmpty()) {
                result.add(line);
                continue;
            }
            List<IssuancePlanLine.SubjectIntent> resolved = new ArrayList<>();
            for (IssuancePlanLine.SubjectIntent subject : subjects) {
                if (!subject.isPerson() || (subject.customerId() != null && !subject.customerId().isBlank())) {
                    String customerId = byOriginalId.getOrDefault(subject.customerId(), subject.customerId());
                    resolved.add(new IssuancePlanLine.SubjectIntent(subject.subjectType(), customerId,
                            subject.subjectName(), subject.subjectSumInsured(), subject.relationToHolder(),
                            subject.attributes()));
                    continue;
                }
                String customerId = uniqueCustomerIdByName(subject.subjectName(), byName);
                if (customerId == null && parties.insuredList() != null && parties.insuredList().size() == 1) {
                    customerId = parties.insuredList().get(0).customerId();
                }
                if (customerId == null) {
                    throw missingIdentity(subject.subjectName() != null ? subject.subjectName() : "人身标的");
                }
                resolved.add(new IssuancePlanLine.SubjectIntent(subject.subjectType(), customerId,
                        subject.subjectName(), subject.subjectSumInsured(), subject.relationToHolder(),
                        subject.attributes()));
            }
            result.add(new IssuancePlanLine(line.lineNo(), line.productId(), line.productCategory(),
                    line.parentLineNo(), line.sumInsured(), line.coveragePeriodValue(), line.coveragePeriodUnit(),
                    line.paymentFrequency(), line.premiumPaymentYears(), List.copyOf(resolved), line.extendData()));
        }
        return List.copyOf(result);
    }

    private void remember(String originalId, String customerId, String name, Map<String, String> byOriginalId,
                          Map<String, List<String>> byName) {
        if (originalId != null && !originalId.isBlank()) {
            byOriginalId.put(originalId, customerId);
        }
        if (name != null && !name.isBlank()) {
            byName.computeIfAbsent(normalize(name), ignored -> new ArrayList<>()).add(customerId);
        }
    }

    private String uniqueCustomerIdByName(String name, Map<String, List<String>> byName) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<String> ids = byName.get(normalize(name));
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().distinct().count() == 1 ? ids.get(0) : null;
    }

    private CustomerResolutionException missingIdentity(String party) {
        return new CustomerResolutionException(CUSTOMER_IDENTITY_REQUIRED,
                party + "缺少客户ID，且姓名、证件类型、证件号不完整");
    }

    private CustomerResolutionException identityMismatch(String message) {
        return new CustomerResolutionException(CUSTOMER_IDENTITY_MISMATCH, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String mask(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private record IdentityKey(IdCardType idType, String idNo) {
        private IdentityKey {
            Objects.requireNonNull(idType, "idType");
            Objects.requireNonNull(idNo, "idNo");
        }
    }
}
