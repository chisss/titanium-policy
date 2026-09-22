package com.titanium.policy.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.view.InsuranceView;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * 投保单读侧查询规约测试（R7-04：未知码不得静默丢弃筛选条件，同 D-501-66）
 * <p>
 * 判据：状态码 {@code valueOf} 失败时必须落入空集谓词（{@code cb.disjunction()}），
 * 而非按原样丢弃条件返回全表——后者在界面上表现为「按某状态筛选却列出全部」。
 * 缺陷现场：前端字典里的 {@code PENDING_AUDIT} / {@code REJECTED} / {@code COMPLETED}
 * 在本域枚举中并不存在，用户选中它们时接口按原样返回全部 19 条。
 * 与 {@code ProposalQueryServiceImpl} 成对：两个查询的语义必须一致，不得只修一处。
 * </p>
 */
class InsuranceQuerySpecificationTest {

    private static final String TENANT_ID = "tenant-1";

    @Test
    void unknownStatusCodeYieldsEmptySetPredicate() {
        Fixture fixture = new Fixture();

        Predicate[] predicates = fixture.invoke("PENDING_AUDIT");

        verify(fixture.criteriaBuilder).disjunction();
        assertEquals(1, Collections.frequency(Arrays.asList(predicates), fixture.emptySet),
                "未知状态码必须产生空集谓词，且落在 AND 组合内");
    }

    @Test
    void knownStatusCodeFiltersNormally() {
        Fixture fixture = new Fixture();

        Predicate[] predicates = fixture.invoke("UNDERWRITING");

        verify(fixture.criteriaBuilder, never()).disjunction();
        assertFalse(Arrays.asList(predicates).contains(fixture.emptySet), "已知状态码不得把结果集清空");
        assertTrue(predicates.length >= 2, "租户隔离 + 状态两个谓词都应存在");
    }

    /** 被测服务与查询规约调用现场：mock 仓储在 findAll 时立即执行规约，取回 AND 组合的谓词数组 */
    private static final class Fixture {

        private final CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        private final Predicate       emptySet        = mock(Predicate.class);
        private final Root<InsuranceView> root        = mock(Root.class);
        private final CriteriaQuery<InsuranceView> criteriaQuery = mock(CriteriaQuery.class);
        private final ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

        @SuppressWarnings("unchecked")
        private Fixture() {
            when(criteriaBuilder.disjunction()).thenReturn(emptySet);
            when(root.get(anyString())).thenReturn(mock(Path.class));
        }

        @SuppressWarnings("unchecked")
        private Predicate[] invoke(String status) {
            InsuranceViewRepository repository = mock(InsuranceViewRepository.class);
            when(repository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(invocation -> {
                Specification<InsuranceView> spec = invocation.getArgument(0);
                spec.toPredicate(root, criteriaQuery, criteriaBuilder);
                return Page.empty();
            });

            new InsuranceQueryServiceImpl(repository)
                    .findInsurancesPageByConditions(null, null, null, status, TENANT_ID, 0, 20);

            verify(criteriaBuilder).and(captor.capture());
            return captor.getValue();
        }
    }
}
