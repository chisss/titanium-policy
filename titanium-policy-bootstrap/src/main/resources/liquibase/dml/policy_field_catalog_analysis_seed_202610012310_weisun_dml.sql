--liquibase formatted sql

-- 保单字段目录「可分析」能力位初值（G07/g07-03/AC-03）：为既有字段批量赋值。
--
-- 【取值口径（2026-10-01 裁示：按需求文档 §g07-03 的隐含口径）】
--   · analyzable   = 1   全部参与分析（都是业务字段，无一项该被排除在分析之外）
--   · aggregatable = 非 TEXT/OBJECT/ARRAY（文档只禁这三类：文本没有可求和的度量语义，
--                     而 ENUM/DATE/DECIMAL 在 COUNT 口径下有意义 —— 枚举分布、日期跨度、金额求和）
--   · groupable    = collection=1 或 ENUM/DATE（离散型可作分组维度；集合字段必须可分组，
--                     否则聚合结果无法按业务主体归并）
--   · filterable   = 1   全部可作筛选条件
--   ⇒ 本批 19 行（10 TEXT / 5 ENUM / 3 DECIMAL / 1 DATE，其中 6 个 collection）的期望分布：
--     analyzable=19 / aggregatable=9 / groupable=11 / filterable=19
--
-- 【为何用 CASE 表达式而不是逐行 UPDATE】
--   表达式把**规则**写在明处（改口径时改一处），且它与 Java 侧的
--   `PolicyFieldDescriptor.validateAnalysis(...)` 是**两套独立实现**——同一份数据，
--   一边是 SQL 谓词、一边是 Java 分支。若两者对规则的理解不一致，读表构造描述符时
--   四条不变量会当场拒绝（fail-closed），不会让不一致的口径静默落库。
--   逐行罗列则两套实现退化为一次性的手工誊抄，无从交叉验证。
--
-- 【为何 UPDATE 不带 WHERE、也不逐一罗列 field_code】
--   本 changeset 是**一次性历史回填**，执行时序由 changelog-master 的 include 顺序锁定：
--       建表(202610011800) → 等价迁移种子(202610011900) → 加列(202610012300) → 本文件
--   即本文件运行时表内**恰好**是那 19 行（Liquibase 在应用启动期跑，g08-06 的 starter
--   是运行期行为，永远排在其后）。逐一罗列 field_code 需要把同一份 19 项清单抄三遍
--   （precondition / UPDATE / rollback），而清单一旦漂移，改一处忘另一处**不会报错**——
--   正是「内联副本随权威源漂移」的典型形态。故清单一个都不写，改用下面的行数下界挡住半迁移。
--
-- 【precondition：既是下界也是上界】
--   count=19 同时断言「种子已跑（≥19）」与「没有预期外的行（≤19）」。
--   若有人删过行、或种子未跑、或该库的表已被其它来源写过，迁移当场 HALT —— 不做部分回填。
--changeset weisun:policy-field-catalog-analysis-seed-202610012310

--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:19 SELECT COUNT(*) FROM t_policy_field_catalog

UPDATE t_policy_field_catalog
SET analyzable   = 1,
    aggregatable = CASE WHEN value_type IN ('TEXT', 'OBJECT', 'ARRAY') THEN 0 ELSE 1 END,
    groupable    = CASE WHEN collection = 1 OR value_type IN ('ENUM', 'DATE') THEN 1 ELSE 0 END,
    filterable   = 1;

--rollback UPDATE t_policy_field_catalog SET analyzable = 0, aggregatable = 0, groupable = 0, filterable = 0;
