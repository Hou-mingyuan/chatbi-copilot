# ChatBI Copilot · 安全说明

本文档说明密钥管理、SQL 护栏与生产部署时的安全基线。**应用当前无内置用户登录**；生产环境应通过网络层与网关控制访问。

## 密钥与敏感配置

| 项 | 存储方式 | 要求 |
| --- | --- | --- |
| `LLM_API_KEY` | 环境变量 / `.env`（勿提交 Git） | 生产使用独立 Key，定期轮换 |
| `CHATBI_SECRET` | 环境变量 | **生产必须修改**默认值；用于 AES 加密数据源密码 |
| 业务库密码 | H2 元库中加密存储 | 界面录入后加密；备份 H2 卷时视同敏感数据 |
| Demo MySQL 账号 | `.env` / compose | **仅用于本地演示**；生产禁用 `DEMO_DATASOURCE_ENABLED` |

### 禁止事项

- 不要将 `.env`、真实 API Key、生产数据库密码提交到仓库。
- 不要在公网直接暴露未鉴权的 ChatBI 实例（见「网络与访问控制」）。
- 不要对生产业务库使用 root 等高权限账号；推荐只读账号 + 最小库表授权。

## SQL 执行安全（核心）

ChatBI 允许 LLM 生成 SQL 并执行，纵深防御如下：

1. **SqlGuard（JSqlParser + 正则兜底）**：仅允许单条 `SELECT` / `WITH ... SELECT`；拒绝 DML/DDL、多语句、`INTO OUTFILE`、`load_file`、`sleep`、`benchmark` 等。
2. **强制 LIMIT**：无 `LIMIT` 自动注入；超过 `max-limit`（默认 5000）收敛。
3. **连接池只读**：目标数据源 JDBC `readOnly=true`，并设置 `maxRows` 与 `queryTimeout`。
4. **人工复核**：高风险环境建议「生成 SQL → 人工确认 → 执行」流程（Roadmap 可扩展审批流）。

实现与单测：`backend/.../text2sql/service/SqlGuard.java`。

## 网络与访问控制

| 场景 | 建议 |
| --- | --- |
| 本地 / 内网演示 | Docker Compose + 防火墙限制端口 |
| 生产 | 前端 Nginx 仅反代静态资源；后端不对外直连业务库端口 |
| 公网 | 必须前置 **VPN / SSO / API Gateway 鉴权**；当前版本无应用层 RBAC |
| MySQL 示例容器 | 默认映射 `13306`；生产勿将 Demo 容器与真实数据混用 |

## 应用层认证与限流 Roadmap（首期 · P2）

> **现状**：MVP 无内置登录；安全依赖 SqlGuard + 网络隔离。以下为首期可落地路径，与 README Roadmap「用户与权限」对齐。

| 阶段 | 能力 | 实现要点 | 验收 |
| --- | --- | --- | --- |
| **Phase 1a** | API Key 网关 | Nginx / Spring `OncePerRequestFilter` 校验 `X-API-Key`；Key 仅环境变量注入 | 未带 Key 返回 401；smoke 脚本带 Key 通过 |
| **Phase 1b** | 按 IP 限流 | Bucket4j 或网关 `limit_req`；默认 60 req/min/Key | k6 超限返回 429，正常流量 P95 不变 |
| **Phase 2** | 用户会话 + 数据源 ACL | Spring Security + H2 用户表；数据源按 `ownerId` 过滤 | 用户 A 不可见用户 B 的数据源配置 |
| **Phase 3** | 行列级权限 | 语义层绑定角色；SqlGuard 追加 `WHERE tenant_id = ?` | 集成测试覆盖越权拒绝 |

**首期推荐（作品集 → 内网试点）**：先落地 **Phase 1a + 1b**（约 1–2 人日），公网演示一律经反向代理终止 TLS 并启用 Key；业务库账号保持只读。

## 依赖与供应链

- 后端：Maven 依赖定期 `mvn versions:display-dependency-updates` 审查。
- 前端：`npm audit`（CI 可选）。
- 基础镜像：定期重建 `backend` / `frontend` Dockerfile 以获取安全补丁。

## 日志与审计

- 查询历史持久化在 H2（`backend-data` 卷）；含自然语言问题、生成 SQL、执行结果摘要。
- 生产建议：集中日志、保留策略合规、限制日志中 LLM Key 与数据库密码输出（当前实现不打印 Key）。

## 生产上线检查清单

- [ ] 修改 `CHATBI_SECRET` 为随机长字符串
- [ ] 设置真实 `LLM_API_KEY`，Key 权限最小化
- [ ] `DEMO_DATASOURCE_ENABLED=false`（或移除 Demo MySQL 服务）
- [ ] 业务数据源使用**只读**数据库账号
- [ ] 配置反向代理 TLS（HTTPS）
- [ ] 限制 `/api` 仅内网或经网关鉴权可达
- [ ] 备份 `backend-data` 卷（H2 元数据）
- [ ] 确认 `chatbi.sql-guard.default-limit` / `max-limit` 符合 SLA

## 漏洞反馈

请在私有渠道联系维护者，勿在公开 Issue 中粘贴 Key、连接串或生产 SQL 样本。
