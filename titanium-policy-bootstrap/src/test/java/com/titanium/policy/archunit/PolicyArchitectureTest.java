package com.titanium.policy.archunit;

import org.junit.jupiter.api.Test;

import com.titanium.buildtools.archunit.AbstractArchitectureGuardTest;

/**
 * 保单域架构守护测试：继承共享基类，仅提供本域根包。
 * 全部 DDD 分层/命名/依赖注入规则由 {@link AbstractArchitectureGuardTest} 提供，
 * 规则一处维护、各域复用，杜绝测试代码复制粘贴漂移。
 */
class PolicyArchitectureTest extends AbstractArchitectureGuardTest {

    @Override
    protected String basePackage() {
        return "com.titanium.policy";
    }

    /**
     * 启用「application 层不得依赖 api 的 DTO」。
     * <p>
     * 保单域 api/web 已按《API层与Web层职责边界及协作规范》整改：DTO→Command 的翻译在 web 完成，
     * application 门面入参即 CQRS 的 Command/Query，不依赖 api DTO。故在本子类覆盖启用。
     * </p>
     */
    @Test
    @Override
    protected void applicationMustNotDependOnApiDto() {
        super.applicationMustNotDependOnApiDto();
    }

    /**
     * 启用「api 层使用 Request/Response 而非 DTO」（2026-07-19 命名新规）。
     * <p>
     * 保单域 api 层已弃用 DTO：写入参 {@code CreatePolicyRequest}/{@code CreateProposalRequest}/
     * {@code ConvertToInsuranceRequest}/{@code AccountValueWriteBackRequest} 落 {@code policy.api.request}，
     * 读出参 {@code PolicyResponse}/{@code PolicyStatusResponse}/{@code InsuranceResponse}/{@code ProposalResponse}
     * 落 {@code policy.api.response}，跨请求/响应共享结构体 {@code Amount}/{@code PolicyItem} 落 {@code policy.api.model}
     * （中性名，无 DTO 后缀）。故停用旧 {@code dtoMustResideInApiLayer}、改启用本规则。
     * </p>
     */
    @Test
    @Override
    protected void apiLayerUsesRequestResponseNotDto() {
        super.apiLayerUsesRequestResponseNotDto();
    }

    /**
     * 启用「web 层使用 DTO/VO 而非 Request/Response」（2026-07-19 命名新规）。
     * <p>
     * 保单域 web 层前端入参已改名 {@code CreatePolicyDTO}/{@code CreateProposalDTO} 等落 {@code policy.web.dto}，
     * 出参 {@code PolicyDetailVO}/{@code InsuranceVO}/{@code ProposalVO} 用 VO，web 层无 Request/Response 后缀类型。
     * </p>
     */
    @Test
    @Override
    protected void webLayerUsesDtoVoNotRequest() {
        super.webLayerUsesDtoVoNotRequest();
    }

    /**
     * 启用「API 契约实现（Provider）须位于 web.provider 且以 Provider 结尾」。
     * <p>
     * 保单域三聚合根契约实现为 {@code PolicyApiProvider}/{@code InsuranceApiProvider}/{@code ProposalApiProvider}，
     * 统一落在 web/provider。故在本子类覆盖启用。
     * </p>
     */
    @Test
    @Override
    protected void apiContractImplMustResideInProviderPackage() {
        super.apiContractImplMustResideInProviderPackage();
    }

    /**
     * 启用「Controller 不得实现 api 契约接口」。
     * <p>
     * 保单域三个 Controller 已去掉 {@code implements XxxApi}，契约实现下沉 web/provider 的 Provider。故覆盖启用。
     * </p>
     */
    @Test
    @Override
    protected void controllerMustNotImplementApi() {
        super.controllerMustNotImplementApi();
    }

    /**
     * 启用「api 层 Feign 契约接口须以 Api 结尾（命名主键为聚合根）」。
     * <p>
     * 保单域按聚合根切分为 {@code PolicyApi}/{@code InsuranceApi}/{@code ProposalApi}。故覆盖启用。
     * </p>
     */
    @Test
    @Override
    protected void apiInterfacesMustBeNamedByAggregate() {
        super.apiInterfacesMustBeNamedByAggregate();
    }

    /**
     * 启用「domain/port 顶层不得平铺类（按对端域拆子包）」（根规约 §3.4.13，m6-910）。
     * <p>
     * 本域 12 个远程端口原为**目录已拆、包名未拆**：文件置于 {@code port/billing} 等子目录，
     * 但 11 个类的 {@code package} 声明仍是扁平的 {@code com.titanium.policy.port}。ArchUnit 断言
     * 基于**字节码包名**而非目录树，故该规则长期形同虚设——新增端口照抄相邻文件即会持续复制该缺陷。
     * 本次已把 11 处 {@code package} 声明改为对端域子包（{@code billing}/{@code clause}/{@code customer}/
     * {@code investment}/{@code payment}/{@code product}/{@code ruleengine}/{@code underwriting}），
     * 并同步全域 33 个引用文件共 48 处 import，故启用本断言固化。
     * </p>
     * <p>
     * 🔴 判据可复用：「**目录重构 ≠ 包重构**」——{@code git mv} 只搬文件、不换包名；凡以 ArchUnit
     * 断言包结构的规约，必须核对 {@code package} 声明而非目录树。
     * </p>
     */
    @Test
    @Override
    protected void portShouldNotContainFlatClasses() {
        super.portShouldNotContainFlatClasses();
    }

    /**
     * 启用「infrastructure/adapter 顶层不得平铺类（按对端域拆子包）」（根规约 §3.4.13，m6-911）。
     * <p>
     * 与 m6-910 的 {@code domain/port} 同源缺陷：11 个 main + 7 个 test 适配器**目录已按对端域拆好**，
     * 但 {@code package} 声明仍是扁平的 {@code com.titanium.policy.infrastructure.adapter}。ArchUnit 断言
     * 基于**字节码包名**而非目录树，故规则长期形同虚设。本次已把这 18 处 {@code package} 声明归位
     * （{@code billing}/{@code clause}/{@code customer}/{@code investment}/{@code payment}/{@code product}/
     * {@code ruleengine}/{@code underwriting}），故启用本断言固化。
     * </p>
     * <p>
     * 实测 adapter 侧**无跨包 import 引用**（全仓 {@code import ...infrastructure.adapter.XxxAdapter;} 计数为 0）：
     * 适配器由组件扫描装配、测试与被测类同子包，故本次改动仅 18 处 package 声明、零 import 改写。
     * </p>
     */
    @Test
    @Override
    protected void adapterShouldNotContainFlatClasses() {
        super.adapterShouldNotContainFlatClasses();
    }

    // 注：早期严格隔离断言 webShouldNotDependOnDomainCommandsOrAggregates 不再启用。
    // 现行 api/web 规范改为 web 直接构造 domain Command / 读侧 FindXxxQuery 作 application 门面入参
    // （主流 Axon/CQRS 做法），web 允许依赖 command/query（但不碰 aggregate），故回退为基类默认 @Disabled。
}
