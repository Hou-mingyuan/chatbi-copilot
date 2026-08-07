# ChatBI Copilot 安全说明

## 安全边界

ChatBI 会执行模型生成或用户重跑的 SQL，因此安全性不依赖单一关键字过滤，而由身份、数据权限、AST 校验、执行计划和数据库只读账号共同保证。

当前版本强制数据源、表、敏感列、导出和语义管理权限；不实现行级权限重写。

## 登录与会话

- 密码使用 BCrypt（cost 12）存储。
- 登录成功后签发高熵随机会话；数据库只保存令牌哈希。
- 会话 Cookie 为 HttpOnly、SameSite=Strict；生产要求 Secure。
- 非安全 HTTP 方法必须同时携带 CSRF Cookie 与请求头。
- 登录失败按客户端和用户名限速；注销会立即撤销服务端会话。
- 所有非公开接口默认要求认证；管理接口还要求 ADMIN。
- `/api/health/live` 和 `/api/health/ready` 仅返回最小探针信息，不暴露配置或数据。

## 数据权限

`DataAccessPolicy` 是统一授权入口：

1. 数据源列表只返回当前用户有 QUERY 权限的记录。
2. Schema 与送入模型的上下文按表和敏感列裁剪。
3. 模型 SQL和手写 SQL 都从 AST 提取真实表、列和通配符，再做授权。
4. `SELECT *` 若可能暴露未授权敏感列，会被拒绝。
5. 历史、会话、收藏和查询任务按用户归属隔离。
6. Excel 导出只接受本人成功查询的 `queryId`，并重新检查当前 EXPORT 权限。

演示策略把 `customers.name` 标记为敏感列：ANALYST 可访问，VIEWER 不可访问。服务端直接提交该列的 SQL 会返回 HTTP 403。

## SQL 与数据库纵深防御

### AST 护栏

- 只接受 JSqlParser 成功解析的单条 `SELECT` / `WITH ... SELECT`。
- 解析失败默认拒绝，不存在字符串兜底放行。
- 拒绝 DML、DDL、多语句、`SELECT INTO`、文件读写、锁和危险函数。
- 检查 CTE、子查询、别名、显式列与投影通配符。
- 自动补充或收敛 LIMIT，并限制 OFFSET。

### 执行计划与资源预算

- MySQL 与 PostgreSQL 使用独立 EXPLAIN adapter。
- 超过确认阈值的扫描先返回 `NEEDS_CONFIRMATION`；超过硬预算直接阻断。
- JDBC Statement 同时设置查询超时、最大行数和取消钩子。
- 异步任务有每用户并发上限、队列上限和 90 秒总超时。

### 数据库账号

- MySQL 只接受 USAGE、SELECT、SHOW VIEW 授权；未知或可写 grant 默认拒绝。
- PostgreSQL 检查 superuser、createdb、数据库 CREATE、可写表和可写 schema。
- 连接池强制只读；保存或重新核验未通过的数据源不能用于查询。
- 演示库写入探针在 MySQL 和 PostgreSQL 均由数据库自身拒绝。

## 结果完整性

- 查询成功后保存规范化列、行和 SHA-256。
- 恢复历史或导出前重新计算并核对哈希，篡改即拒绝。
- 表格、图表、确定性解读和 Excel 全部读取同一快照。
- 超过 JavaScript 安全整数精度的数值以字符串保存，并回退无损表格。
- Excel 对超 15 位整数采用文本单元格；以 `= + - @` 开头的文本会中和，防止公式注入。

## 密钥与敏感配置

| 配置 | 要求 |
| --- | --- |
| `LLM_API_KEY` | 仅环境变量或未跟踪的 `.env`；不得写入日志、源码或报告 |
| `CHATBI_SECRET` | 用于 AES-GCM 加密数据源密码；生产必须使用随机值并安全备份 |
| 数据库口令 | 目标库使用独立最小权限只读账号 |
| 演示口令 | 只用于本地；生产必须关闭 `DEMO_AUTH_ENABLED` |

仓库级扫描：

```bash
node scripts/check-secrets.mjs
```

## 生产启动闸门

`CHATBI_PRODUCTION_MODE=true` 时，以下任一情况会阻止启动：

- `CHATBI_SECRET` 为空或仍为开发默认值；
- 演示认证仍启用；
- Cookie 未启用 Secure；
- CORS 使用通配来源。

生产还应设置：

```dotenv
CHATBI_PRODUCTION_MODE=true
CHATBI_SECRET=<random-secret>
CHATBI_COOKIE_SECURE=true
CHATBI_ALLOWED_ORIGINS=https://bi.example.com
DEMO_AUTH_ENABLED=false
DEMO_DATASOURCE_ENABLED=false
DEMO_PG_ENABLED=false
```

## 上线检查清单

- [ ] 仅通过 HTTPS 暴露前端；后端和目标数据库不直接暴露公网。
- [ ] 关闭演示账号、演示数据源和默认口令。
- [ ] 设置并备份随机 `CHATBI_SECRET`。
- [ ] 为每个业务库创建独立只读账号，并在 UI 中通过只读核验。
- [ ] 按用户授予数据源、表、敏感列、导出和语义管理权限。
- [ ] 校准查询超时、行数、扫描确认和硬阻断预算。
- [ ] 备份元数据库并验证恢复；恢复时使用相同加密密钥。
- [ ] 将审计日志接入保留和告警策略。
- [ ] 运行后端测试、真实双库测试、前端质量门槛、Mock smoke 和密钥扫描。

## 可复核测试

```bash
cd backend
mvn -Dtest=SecurityIntegrationTest,DataAccessPolicyTest,SecureQueryValidatorIntegrationTest,SqlGuardTest,HistorySecurityIntegrationTest,ExcelExportServiceTest test
mvn -Dtest=RealDatabaseSafetyIT test
```

测试覆盖 401/403、CSRF、注销撤销、跨用户访问、敏感列、通配符、历史/快照/导出撤权、危险 SQL、双库只读授权和写入拒绝。

## 漏洞反馈

请通过私有渠道报告，勿在公开 Issue 中粘贴密钥、连接串、Cookie、生产 SQL 或业务数据。
