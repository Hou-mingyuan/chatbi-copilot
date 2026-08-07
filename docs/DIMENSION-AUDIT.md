# ChatBI Copilot 验收证据索引

本文件不做主观打分，只列可复现门槛和证据位置。最新命令结果以当前工作区的测试报告、`reports/` 与 CI 为准。

| 维度 | 证据 |
| --- | --- |
| 登录与权限 | `SecurityIntegrationTest`、`DataAccessPolicyTest`、`SecureQueryValidatorIntegrationTest` |
| MySQL/PostgreSQL 只读 | `RealDatabaseSafetyIT`、`sample-data/*/03_readonly_user.sql` |
| SQL 护栏 | `SqlGuardTest`、`SecureQueryValidatorIntegrationTest` |
| 语义层 | `SemanticServiceTest`、真实浏览器版本与 Prompt 预览 |
| 澄清与多轮 | `ClarificationPolicyTest`、`PromptBuilderTest`、`HistorySecurityIntegrationTest` |
| 异步执行 | `QueryJobServiceIntegrationTest` |
| 结果/图表/解读 | `ChartRecommenderTest`、`ResultInterpreterTest`、真实浏览器截图 |
| 历史/收藏/Excel | `HistorySecurityIntegrationTest`、`ExcelExportServiceTest`、真实导出文件检查 |
| 真实模型 | `eval/nl2sql-eval-v1.json`、`reports/nl2sql-eval-glm-5.2.json` |
| 前端交互 | Vitest、lint、typecheck、build、三视口真实截图 |
| 性能 | `loadtest/dry_run.py`、`performance/k6-smoke.js`、`PERFORMANCE_REPORT.md` |
| 开箱启动 | `docker-compose.yml`、`scripts/smoke-mock-demo.mjs`、CI `docker-smoke` |
| 仓库卫生 | `scripts/check-secrets.mjs`、`git diff --check`、CI `repository-hygiene` |

## 固定事实

- 宿主机运行端口限定为 `19030-19039`。
- Mock 只用于流程回归，真实模型结果单独报告。
- v1 强制数据源、表和敏感列权限；未实现行级权限。
- 图表、数据解读和 Excel 均来自带 SHA-256 的同一结果快照。
- 旧缓存镜像、占位 SVG、只检查 HTTP 200 的压测结果均不作为完成证据。

## 真实浏览器截图

- `docs/screenshots/chat-desktop.png`：`1440×900`
- `docs/screenshots/chat-tablet.png`：`768×1024`
- `docs/screenshots/chat-mobile.png`：`375×812`

## 复现顺序

1. `mvn test`。
2. 启动 MySQL/PostgreSQL 后运行 `RealDatabaseSafetyIT`。
3. 运行前端 lint、typecheck、Vitest 和 build。
4. 启动当前源码，运行 Mock smoke、认证态性能和真实模型固定集。
5. 用真实浏览器逐页检查登录、权限、数据源、语义、问数、历史、收藏、导出和管理页。
6. 运行密钥扫描、`git diff --check` 与最终 Git 状态检查。
