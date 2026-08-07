# ChatBI Copilot 发现与决策

## 需求事实

- 只允许修改 `D:\project-hub\chatbi-copilot`。
- 不提交、不推送、不发布；运行服务只使用 `19030-19039`。
- 完成判断同时受“全项目统一完成标准”和“3. ChatBI Copilot”约束。
- 最终必须证明 MySQL/PostgreSQL 查询安全、权限不可绕过、结果数字与图表一致。
- 必须使用真实浏览器与固定问数评估集；Mock 准确率不能作为真实模型效果。

## 基线审计与已验证修复

下方前半部分保留 2026-07-19 改动前基线，用于解释修复根因；完成态以本文“能力矩阵”、`progress.md` 最新测试结果和仓库报告为准。

- 2026-07-19 初始 `git status --short --branch` 仅显示 `## main...origin/main`，工作树干净。
- 仓库包含 backend、frontend、Docker Compose、部署/安全/性能文档、脚本和样例数据。
- 验收编排技能引用的 `create-issue-gate`、`closed-loop-delivery`、`verification-before-completion` 未安装；本任务直接执行等价的门槛、闭环和新鲜证据要求。
- 仓库内不存在额外 `AGENTS.md`，当前执行规则来自用户在 Goal 中提供的全局指令。
- README 当前 Roadmap 明确把“用户与权限、行列级数据权限”“SQL 执行计划与耗时可视化”“结果自然语言总结”“SSE 与阶段展示”列为未完成，均是本次硬门槛。
- README 仍引用 `8888/8080/13306/18182/18183` 等旧端口，后续必须统一到用户限定的 `19030-19039`。
- 工作目录存在 `frontend/node_modules`、`frontend/dist`、`backend/target`；需核对是否被 Git 跟踪及仓库卫生规则，不能把本地缓存误当源码能力。
- `.gitignore` 已排除 `backend/target`、`frontend/node_modules`、`frontend/dist`、`.env`、日志和本地数据库；`git ls-files` 证明这些现存缓存/产物未被跟踪。
- 当前受控后端源码没有 auth/user/role/permission 包或相关测试；权限功能不是隐藏在既有模块中，而是缺失。
- 当前后端仅有 4 个测试类（配置、Mock LLM、Prompt、SQL Guard），未见控制器、数据源、执行、导出、历史、语义、MySQL/PostgreSQL 集成或权限测试。
- `backend/pom.xml` 没有 Spring Security/JWT、迁移工具或 Testcontainers 依赖，无法支持当前要求的登录安全和双数据库可重复集成验收。
- `frontend/package.json` 只有 dev/build/test，缺少 lint、typecheck 和 Playwright E2E 脚本；现有依赖中也没有端到端测试工具。
- `docker-compose.yml` 仅编排 MySQL/后端/前端，没有 PostgreSQL；MySQL 应用用户由镜像变量创建，对演示库具备写权限，不能证明数据库账号只读。
- Compose 默认宿主端口为 `13306/8080/8888`，README 还出现 `18182/18183`，全部不符合本 Goal 的 `19030-19039` 限制。
- `application.yml` 默认启用 H2 Console，当前又无认证层；Swagger/API 同样公开。
- 开发与 Compose 使用可预期的默认加密密钥和数据库口令；零密钥 demo 可以保留安全演示默认，但生产模式必须有启动时强制检查且文档明确边界。
- 本地配置默认 `LLM_PROVIDER=deepseek` 且无 Key，而 `.env.example`/Compose 默认 mock，原生开箱路径的默认行为不一致。
- `SECURITY.md` 明示当前无应用登录，公网依赖 VPN/SSO/API Gateway；数据源 ACL 与行列权限只是 Roadmap，无法满足本次服务端权限闭环。
- `docs/architecture.md` 描述 SQL 解析失败后用关键字兜底并继续放行；专项目标要求“解析失败默认拒绝”，当前设计本身不合格。
- 安全文档目前只“推荐”业务库只读账号，没有 Compose 初始化、启动检查或自动化证据强制这一点。
- CI 仅执行 Maven test、npm audit/build/test 和单 MySQL Compose smoke；缺少 lint/typecheck、PostgreSQL、权限/SQL 绕过、浏览器 E2E、secret scan 与 `git diff --check` 门槛。
- `PERFORMANCE_REPORT.md` 记录了旧的 50 VU 数字，但没有随仓库保存的原始结果文件，且统一完成标准要求本次先测新鲜基线再优化，不能直接沿用。
- 性能文档把 `/api/datasources` 与 `/api/query/run` 一并设为 p95 < 800ms；本 Goal 应按普通读接口 <= 300ms、核心本地写接口 <= 800ms 分级报告，外部 LLM 单列。
- 部署文档与 README 均使用 Goal 范围外端口，且生产部署说明仍建立在“无应用认证”的旧前提上。
- `schema-h2.sql` 只有数据源、历史、收藏、基础语义 4 张表；所有记录都没有用户/租户归属，亦无用户、角色、权限、会话、审计、语义版本或指标/JOIN/枚举模型。
- `SqlGuard.sanitize` 捕获 AST 解析异常后做字符串扫描并放行，直接违反 fail-closed；解析成功路径也未拒绝 `FOR UPDATE/SHARE`、PostgreSQL `SELECT INTO` 等带写入/锁语义的查询。
- LIMIT 仅在字面量为 `LongValue` 时收敛；参数化/表达式 LIMIT 和超大 OFFSET 未拒绝，扫描风险未受控。
- 危险函数依赖少量正则，包括 `sleep/pg_sleep/load_file/benchmark`；没有从 AST 遍历函数和查询子结构，也没有数据库层只读证据兜底。
- `DynamicConnectionManager` 的 Hikari/JDBC `readOnly=true` 只是连接属性；`testConnection` 仅调用 `isValid`，不验证数据库授权是否真的拒绝 INSERT/DDL。
- 数据源配置可自由拼接 `jdbcParams`，缺少参数 allowlist；MySQL URL 还固定 `useSSL=false`，不适合生产安全默认。
- `SqlExecutor` 使用固定 30 秒 timeout，没有执行计划/扫描风险分析、取消句柄或行级权限参数；SQL 异常原文直接返回，可能泄漏库结构。
- 查询恰好返回 `maxRows` 时 `truncated=true`，但没有多取一行确认，截断状态可能错误。
- 所有 DataSource/Query 控制器匿名开放；未保存连接测试允许任意 host/port，构成服务端网络探测（SSRF）面。
- `DataSourceService` 所有列表/读取/修改均为全局查询，没有 owner/角色/ACL 过滤；删除也未见关联数据清理或审计。
- `AskRequest.history` 由客户端直接传给 Prompt，服务端没有会话实体或所属校验，可跨用户/数据源伪造上下文。
- 澄清完全依赖模型返回 `needClarification`，没有针对时间范围、指标口径、分组维度或目标表的确定性策略。
- SQL Guard 在历史记录 try/catch 之外，Guard 拒绝、LLM 失败、解析失败不会形成完整审计；preview-only 却被写成 success，语义不准确。
- `QueryResult.explanation` 来自 SQL 生成阶段的模型回答，未使用实际结果集，不能作为“基于结果数据的自然语言解读”。
- 问数 API 是同步 REST；没有阶段事件、取消、请求超时/重试状态或服务端任务标识。
- `PromptBuilder` 无条件渲染整个可见 Schema，没有基于问题/术语的轻量检索、表数/字符预算或权限裁剪；历史轮数与长度也无上限。
- 数据库注释和管理员录入的语义文本直接拼入 system prompt，没有明确的不可信数据边界或提示注入防护。
- `SchemaInspector` 只读取表、列、主键，未读取外键/JOIN；PostgreSQL 固定 `public` schema。
- Schema 缓存对象被 `SemanticService.applyTo` 原地修改，语义删除/变更后可能保留旧字段值；语义 CRUD 也未主动失效缓存。
- 语义模型只有别名/描述，缺少指标公式与聚合、单位、时间粒度、枚举、JOIN、版本、发布状态和 Prompt 预览。
- 问句只有 `@NotBlank`，没有长度限制；history 元素也无嵌套校验或总预算，存在资源滥用与上下文污染风险。
- 历史/收藏记录没有用户、会话或数据源 ACL，分页 size 无上限，匿名用户可枚举、删除或清空全部记录。
- `HistoryService.record` 吞掉写入异常；安全审计可能静默丢失，且未记录操作者、阶段、修改 SQL、结果摘要、风险决策或 request id。
- 收藏直接接收并保存客户端 SQL，没有校验其来源/数据源权限；重跑时依赖 `/query/run` 的同一匿名入口。
- Excel 导出端点接收 SQL 并重新执行，不绑定某次已授权查询结果；数据变化时屏幕与文件可不一致，也无法证明“只导出本人可见结果”。
- Excel 文本未防 `= + - @` 公式注入；所有 Number 转 double，长整型/高精度小数可能丢精度。
- 图表推荐只按 JDBC 类型/列数启发式判断，没有语义层角色/单位；饼图未校验负数/空值/总和，整型时间维度和多维聚合可能误判。
- `GlobalExceptionHandler` 返回普通 envelope 而不设置 HTTP status，业务/校验/服务器错误在传输层均可能是 200；前端重试、权限状态和监控会误判。
- 未知异常响应拼接 `ex.getMessage()`，可能泄漏 JDBC、路径或内部实现信息。
- CORS 使用任意 origin pattern 并允许 credentials，不能作为带登录会话的安全默认。
- `PasswordCipher.decrypt` 静默接受无 `enc:` 前缀明文，生产配置也不校验默认密钥；缺少启动失败闸门和密钥轮换策略。
- `/health` 固定返回 UP，只报告 LLM 配置，不检查元数据库；没有 readiness/liveness 区分或 request id。
- 前端没有登录路由、用户态、权限路由或权限页面；所有导航默认对所有人可见。
- ChatView 的阶段状态由前端 `setInterval` 轮播，结果说明由打字机动画模拟，既非 SSE 也非服务端真实阶段；没有 AbortController/取消 API。
- 多轮历史只保存在 ChatView 内存；切换全局数据源不会清空 `turns/messages`，旧数据源 SQL 会作为新数据源 Prompt 历史发送，存在明确跨数据源串用。
- Axios 固定 120 秒超时；请求错误既由拦截器 toast 又在消息气泡展示，可能重复反馈；应用首屏加载数据源失败没有局部 retry/offline 状态。
- `ChartRenderer.toNumber` 将 null、非数值和溢出值变成 0，表格与图表会出现可证明的不一致；未使用服务端 `seriesField`，多维数据推荐无法正确渲染。
- 全站固定 200px 侧栏与宽头部控件，现有 CSS 没有移动端 media query；大量 12px/小按钮和圆形文字 chip，不符合触控与响应式门槛。
- 现有视觉为紫色渐变、emoji 品牌与通用卡片式布局；后续按数据工具的安静、专业、扫描友好方向统一，不做营销式装饰。
- 结果面板声称“完整结果请导出 Excel”，但导出与页面使用同一 max rows 上限且重跑 SQL，这条文案不真实。
- 数据源表单只做前端手写的 3 字段非空判断，没有 Element Plus rules、字段级后端错误、测试通过标记或“数据库账号只读”验证反馈；任意 JDBC 参数直接暴露给用户。
- 语义页面只有表/字段别名和描述，没有保存 loading/防重复、版本历史、指标/枚举/时间/JOIN 管理或 Prompt 上下文预览。
- 历史单条删除无确认；未选择数据源时“清空当前数据源”实际调用无 datasourceId 的全局清空，文案与行为冲突。
- 多个页面仅依赖瞬时 toast 表示错误，没有持久 error + retry/offline/permission 状态；弹窗/抽屉使用固定 `520/560px`、`42%/70%`，移动端不可用。
- 前端全量注册 Element Plus 与全部图标，可能扩大首屏包；需用构建产物和 Lighthouse 基线决定是否优化。
- Vite 开发端口 5173、代理 8080；Nginx 代理 300 秒且无安全头/SSE 禁用缓冲配置；均需按 Goal 端口和实时状态链路校准。
- 后端 Dockerfile 打包使用 `-DskipTests`，Compose smoke 本身不能证明镜像源码通过测试。
- `performance/k6-smoke.js` 查询不存在的 `sales_orders`，却只断言 HTTP 200；结合全局异常总是 200，会把 SQL 业务失败统计为成功，旧性能结果属于不可采信的假绿。
- `loadtest/dry_run.py` 和另一份 k6 脚本同样只读取/断言 HTTP 状态，不验证 envelope `code`、结果行或固定数字。
- Mock LLM 对所有不识别问题统一返回“已支付订单总数”，从不澄清/拒绝；它只能用于确定性演示，不能用于准确率或泛化结论。
- 没有 30+ 固定评估集、执行结果等价判定器、方言预期、澄清/安全分类或报告脚本。
- `docs/DIMENSION-AUDIT.md` 把写入 Roadmap 的认证/限流标为完成并给安全 8 分、UX 9 分，和代码事实冲突；需删除自评分式虚假结论或改为可复现证据。
- README 截图是 `docs/screenshots/*.svg` 占位图，不满足真实运行截图门槛。
- `docs/USAGE.md` 仍查询不存在的 `sales_orders`，并与当前 Compose 默认 Mock 的自然语言能力说明冲突。
- 改动前基线：Maven 22 tests pass；Vitest 12 tests pass；Vite build pass；Docker Desktop/Engine 可用。
- Vite 基线 bundle：`elementplus` 1,120.76 kB（gzip 351.96 kB）、`echarts` 1,084.65 kB（gzip 358.61 kB），首屏/按需加载仍有明确优化空间。
- Vite 8/Rolldown 对 `@vueuse/core` 输出两条 `INVALID_ANNOTATION` 第三方警告；需要在最终浏览器控制台与构建日志中区分是否阻断。
- Compose 缓存镜像可在 19030-19032 启动，但 `provider=mock` 时 `/llm/status` 仍返回 `configured=false`，现有 Mock smoke 在该门槛失败；缓存镜像与当前源码/单测不一致，不能作为源码证据。
- Docker 日志显示旧 H2 卷已有 1 个数据源，initializer 因“任意数据源已存在”跳过 demo 注册；该策略也会阻止后续自动补齐 PostgreSQL demo。
- 全新 `chatbi-acceptance` 卷已在 19032/19033 初始化 MySQL 8.0.46 与 PostgreSQL 16.14；两库相同种子聚合均为已支付订单 20、金额 185551.00。
- MySQL `chatbi_ro` 实际 grants 只有全局 USAGE 与 `chatbi_demo.*` 的 SELECT/SHOW VIEW；UPDATE 被数据库以错误 1142 拒绝。
- PostgreSQL `chatbi_ro` 实测 `usesuper=false`、`usecreatedb=false`、database CREATE=false；UPDATE 被默认只读事务拒绝。
- 新查询链已经忽略客户端 history，只读取同用户、同数据源、同服务端 session 的成功历史。
- 成功结果的表格、图表定义、确定性解读和 Excel 均绑定同一快照；快照读取会复核 SHA-256。
- 旧查询不会形成永久导出授权：Excel 每次按 queryId 重新检查本人归属与当前 EXPORT 权限。
- 超过浏览器安全精度的 JDBC 整数/小数会变为精确字符串，图表对这类列回退表格，避免静默舍入。
- MySQL 账号核验已从写权限黑名单改为只允许 USAGE/SELECT/SHOW VIEW；未知或角色型 grant 默认拒绝。
- PostgreSQL 核验现在检查高权角色、数据库 CREATE、实际可写表和可写 Schema，不再只看直接 grant 行。
- `RealDatabaseSafetyIT` 已通过当前源码直连 19032/19033：MySQL/PostgreSQL 均读取 4 表、EXPLAIN 成功、聚合为 20 单和 185551.00，连接池 UPDATE 均抛 SQLException。
- 查询任务状态持久化到 V2 `query_job`；服务端阶段、取消、总超时和重试已有竞态测试，不再依赖前端假进度。
- 服务端澄清策略覆盖首轮指代、纯筛选、模糊时间、缺指标和非只读/绕权意图；有合法服务端历史时允许短句继承上下文。
- 语义写入现在校验目标库真实表列、时间类型和 JOIN 两端；指标表达式复用 SQL Guard 且只能引用声明表。

## 能力矩阵

| 能力 | 宣称状态 | 代码证据 | 测试/运行证据 | 当前判断 |
|------|----------|----------|---------------|----------|
| 登录与 RBAC | README 已与实现一致 | `auth` 服务端会话、BCrypt、CSRF、限速、ADMIN/ANALYST/VIEWER | `SecurityIntegrationTest`、真实浏览器登录/注销/受保护路由 | pass |
| 数据源/表/敏感列权限 | v1 明确覆盖；不宣称行级规则 | `DataAccessPolicy` 统一作用于 Schema、Prompt、模型/手写 SQL、历史、收藏和导出 | `DataAccessPolicyTest`、`SecureQueryValidatorIntegrationTest`、`HistorySecurityIntegrationTest` | pass |
| SQL Guard | README/架构/安全文档一致 | 单条 Select AST、解析失败默认拒绝、资源提取、LIMIT/OFFSET 和危险函数规则 | `SqlGuardTest` 17/17；双库 IT 再验证写 SQL/文件函数/锁语句拒绝 | pass |
| MySQL/PostgreSQL | Compose 双库开箱 | 独立只读账号核验、方言 adapter、EXPLAIN、独立连接池 | `RealDatabaseSafetyIT`、全新卷双库 smoke；均返回 `20 / 185551.00`，UPDATE 由数据库拒绝 | pass |
| 数据源管理 | CRUD/连接测试/Schema/只读证据 | 管理员边界、凭据加密、主机/参数政策、只读核验后才可查询 | JDBC 参数、权限和双库集成测试；浏览器页面验收 | pass |
| 语义层 | 六类定义、版本与 Prompt 预览 | 表/字段别名、指标、时间粒度、枚举、JOIN、revision 及有界检索 | `SemanticServiceTest`、`PromptBuilderTest`、浏览器创建/版本/预览 | pass |
| 问数/澄清/多轮 | 服务端会话闭环 | 确定性澄清，上下文按用户+数据源+会话隔离，忽略客户端伪造 history | 澄清、Prompt、任务集成测试；固定集策略 11/11 | pass |
| 执行/图表/解读 | 与结果快照绑定 | EXPLAIN 风险、超时/取消/重试、结果解读、类型化图表建议和正确 camelCase JSON 契约 | 双库 IT、图表/精度单测、双库 E2E、浏览器 `7/6/3/2/2` 一致 | pass |
| 历史/收藏/Excel | 不可变快照闭环 | 本人归属、当前 ACL 复核、SHA-256、收藏幂等、Excel 精度/公式保护 | 快照/跨用户/撤权/篡改集成测试；双库完整 E2E 真实下载 XLSX | pass |
| 固定评估集 | 34 个唯一问题、57 次执行 | runner 拒绝 Mock，以参考 SQL 和生成 SQL 的真实执行结果等价评分 | 最新真实模型 `43/46`，策略 `11/11`，评估器 5/5 | pass |
| 页面/交互/移动端 | 紧凑数据工作台 | 真实任务阶段、取消/重试、六类状态、按需加载和响应式导航 | 三视口 Chromium 无溢出/遮挡，三张真实结果截图，控制台 0 error | pass |
| 性能/开箱启动 | 零密钥 Java 17 Compose | 双库迁移/种子自动化、health/readiness、自动 smoke、首屏拆包 | 全新卷启动、15/15 性能通过、Lighthouse 90/100/100 | pass |

## 技术决策

| 决策 | 理由 |
|------|------|
| 先跑基线再修改 | 需要区分既有失败与改动回归，并为性能提供前后数据 |
| SQL 安全同时依赖 AST/解析器、权限重写和数据库只读账号 | 单一字符串黑名单不能证明安全 |
| 对图表、总结、表格和 Excel 使用同一规范化结果模型 | 降低数字不一致风险并便于自动验收 |
| 认证采用服务端可撤销随机会话，HttpOnly/SameSite Cookie + CSRF 双提交 | 避免不可撤销长效 JWT 和 localStorage 长期令牌；浏览器与 CLI 均可验证 |
| `DataAccessPolicy` 是数据权限的唯一深模块接口 | 数据源列表、Schema/Prompt、SQL、历史、收藏、导出统一过同一 seam，避免控制器散落判断 |
| v1 强制数据源、表和敏感列权限，不声称行级权限 | 专项目标对行级规则是条件要求；先把明确必需的三层权限做成不可绕过闭环 |
| SQL 只接受 JSqlParser 成功解析的单条 Select AST | 解析失败 fail-closed；字符串规则只作附加危险特征拦截，不作放行依据 |
| 查询改为服务端异步任务 + 轮询真实阶段 + DELETE 取消 | 满足清晰阶段、超时、取消和重试，避免伪 SSE/定时假进度的复杂性 |
| 查询成功后持久化规范化结果快照及哈希，Excel 按 queryId 导出 | 不重跑 SQL；保证表格、图表、解读和下载来自同一数据并可授权复核 |
| 语义模型保留统一定义表并增加类型化字段、版本号与 revision 表 | 在现有代码上最小迁移，同时覆盖别名、指标、枚举、时间和 JOIN 的管理/版本需求 |
| 元数据 Schema 改由 Flyway 版本迁移 | 新装可复现，已有 H2 可做可追溯增量升级，避免 `schema.sql` 漂移 |
| MySQL/PostgreSQL 以两个方言 Adapter 实现只读账号检查与 EXPLAIN | 两个真实实现形成合理 seam；共享风险模型和集成测试接口 |
| 前端采用安静、紧凑的数据工作台风格 | 面向重复扫描和操作；去除紫色渐变、emoji 与营销式卡片，统一三视口体验 |

## 遇到的问题

| 问题 | 解决方案 |
|------|---------|
| 总目标一次性输出被截断 | 已按行号完整读取 ChatBI 专项目标；统一标准在截断前已完整读取 |

## 视觉/浏览器发现

- 真实浏览器三视口和核心流程验收已完成；登录、问数、数据源、语义、历史、收藏、权限页面均已覆盖，结果数字、表格、图表无障碍描述和 Excel 使用同一结果快照。
- 2026-07-20 登录页 Lighthouse 首轮：Performance 33、Accessibility 95、Best Practices 96；FCP 9.1s、LCP 10.4s、TBT 1.14s、CLS 0。主要未通过项是首屏加载/主线程工作、未使用的 CSS/JS、缓存策略、控制台错误和一处颜色对比，需要定位并复测，当前不能作为性能完成证据。
- Lighthouse 网络证据显示 HTML TTFB 约 75ms，低分不是后端响应造成；匿名登录页预加载完整 Element Plus（JS 1,121,469 bytes、CSS 356,999 bytes），其中脚本启动占约 1.17s，且 Nginx 未给哈希静态资源缓存头。
- Best Practices 的控制台错误仅来自登录页启动时探测 `/api/auth/me` 得到预期 401；Accessibility 的唯一失败是 `.eyebrow`、`.login-help`、`.login-footnote` 使用 `#71817d` 白底，对比度 4.08:1。
- 依赖追踪确认 `main.js` 全量安装 Element Plus 并全量注册图标，根 `App.vue` 又直接包含只在认证后使用的工作区壳层，导致登录路由即使页面组件懒加载也无法减小首屏；修复方向是受保护父路由懒加载工作区布局、Element Plus 按组件导入、图标显式小集合导入。
- Pinia 认证状态机允许匿名公共路由跳过会话恢复：登录成功会设置 `authReady/user` 并加载工作区，而任何受保护路由在 `authReady=false` 时仍强制调用 `/auth/me`；因此可消除登录页预期 401，且不改变受保护页面边界。
- 首屏拆包首轮实现后，前端 lint/typecheck/build 和 22 个测试全部通过；Element Plus 构建块从 1,121KB 降为 481KB，但登录页仍使用其输入和按钮。为确保公共首屏不加载工作区组件库，登录表单将改用语义化原生控件，认证后页面继续按需使用 Element Plus。
- 登录表单改成原生控件后的产物检查仍发现 `dist/index.html` 直接预加载 Element Plus 482KB JS / 169KB CSS；说明公共入口还有组件库引用或手工 `manualChunks` 将其提升为入口依赖，必须继续消除，不能仅以页面源码未引用判定拆包完成。
- 删除 Element Plus 的手工 `manualChunks` 后，最终 `dist/index.html` 仅预加载应用入口 71KB、Vue 运行时 104KB 和主 CSS 9.5KB，不再引用 Element Plus、工作区布局或 ECharts；组件库和图表资源保留为认证后路由懒加载。
- 源码镜像 `sha256:dad021f...` 在 `19035` 真实 Chrome Lighthouse 复测：Performance 90、Accessibility 100、Best Practices 100；FCP 2.2s、LCP 2.4s、TBT 270ms、CLS 0，首屏 6 个请求共 192,662 bytes。首轮的预期 401、三处颜色对比和无缓存问题均已消失。
- Playwright 真实 Chromium 在 375x812 检查登录页：品牌、主标题、用户名/密码、错误区域、登录按钮和 Cookie 说明均在首屏内完整呈现，焦点环清晰，无可见横向溢出、遮挡或文字截断；截图保存在本地 `.playwright-cli/page-2026-07-20T03-27-20-701Z.png`。
- 同一 375x812 Chromium 会话使用管理员真实登录后进入受保护工作区，默认 PostgreSQL 数据源固定问句“已支付订单的数量和销售额是多少？”返回 1 行；解读/摘要为 `20` 与 `185551.00`，表格为 `20` 与格式化显示 `185,551`，SQL 显示 LOW 风险，证明首屏拆包未破坏登录、路由和基础问数。
- 浏览器追问“各大区的已支付订单数量分别是多少？”曾暴露 Mock 意图匹配错误：生成 SQL 查询 `customers` 并返回 customer_count `3/3/2/2/2`，而非已支付订单数。该问题随后已通过匹配优先级修复、9 项回归测试和双库 E2E 关闭；Mock 仍不计真实模型准确率。
- 根因定位为 `MockLlmChatClient` 把“区域 + 数量”直接当作客户数量，且该分支位于已支付订单总量之前；修复应新增优先的“区域 + 订单 + 数量”分支，并要求客户数量分支显式出现“客户/人数/customer”。
- 修复后 9 个 Mock 定向测试通过；源码后端镜像 `sha256:a0454a77...` 在 `19034` 运行。375x812 Chromium 对 PostgreSQL 复验同问句，SQL 正确查询 `orders JOIN customers WHERE status='paid'`，表格和饼图描述逐项同为华东 7、华南 6、华北 3、华中 2、西南 2，解读最高值华东 7。
- 375x812 Chromium 的数据源选择器需点击可点击父容器；点击内部被覆盖的 `input` 会触发定位超时，但真实用户入口可正常展开。
- 同一真实浏览器会话已从 PostgreSQL 成功切换到 `Demo - Sales (MySQL)`；切换入口可用，并触发跨数据源对话清理。
- 已在 MySQL 数据源提交与 PostgreSQL 完全相同的固定问句“各大区的已支付订单数量分别是多少？”，待终态快照核对等价结果。
- MySQL 终态 SQL 正确查询 `orders JOIN customers WHERE status='paid'`；饼图无障碍描述逐项为华东 7、华南 6、华北 3、华中 2、西南 2，解读最高值为华东 7，与 PostgreSQL 完全一致。
- MySQL 数据表也逐行显示华东 7、华南 6、华北 3、华中 2、西南 2；375x812 页面根节点和 body 的 `scrollWidth` 均为 375，无横向溢出，控制台 0 error / 0 warning。
- 768x1024 平板快照中数据源、账号身份、会话、LOW 风险 SQL、结果摘要、表格、输入区和底部导航均保留完整语义结构，五区结果未因响应式切换改变。
- 768x1024 真实截图发现顶栏“当前数据源”被压成三行且选择器仅显示 `D...`；功能未阻断，但扫描体验不达标，需针对平板断点修正后复验。
- 平板计算样式确认 `.topbar-context` 仅 115.95px、标签 35.44px、数据源选择器 68.52px，三者均允许 flex 收缩；根因是 641-900px 断点没有给上下文分配剩余宽度，也未禁止标签换行。
- 平板 CSS 修复通过 lint、typecheck、22/22 测试和生产构建；新前端镜像 `sha256:884fbf...` 已在 `19035` 返回 HTTP 200，重载后认证会话仍停留在 `/chat`。
- 独立应用内 Playwright Chromium 已正常打开新镜像登录页；1036x703 初始快照的品牌、说明、用户名、密码、登录按钮和 Cookie 说明边界均完整，替代失去响应的旧 CLI 会话。
- 768x1024 登录页两栏保持完整：左侧品牌/价值说明/三项信任事实和右侧 328px 登录表单均在视口内，无元素交叠或文字截断。
- 新 Chromium 使用管理员账户在 768x1024 成功登录并进入 `/chat`，证明前端镜像替换后认证 Cookie、受保护路由与后端连接正常。
- 平板顶栏修复后 `.topbar-context` 从 115.95px 扩为 573px，标签为单行 59.95px、选择器恢复 270px；根节点和 body `scrollWidth` 均为 768，无横向溢出。
- 更新后的 768x1024 真实截图已人工复核：数据源名称可读、账号区不重叠，标题、Mock 边界提示、六个示例问题、输入区和六项底部导航均完整且对齐。
- 更新后的 768x1024 页面控制台为 0 error / 0 warning；同一真实 Chromium 已切换到 1440x900 桌面视口继续验收。
- 1440x900 计算样式发现桌面仍复现原顶栏问题：上下文 115.95px、标签 35.42x45.6px、选择器 68.53px；说明首轮修复只覆盖 `<=900px`，需把同一约束提升为全局。
- 全局修复后的最终镜像在 1440x900 显示顶栏上下文 1013px、标签单行 60px、选择器 270px；左侧导航、账号区和主工作区边界无重叠。
- 1440x900 根节点和 body `scrollWidth` 均为 1440，无横向溢出；最终桌面截图已写入 `docs/screenshots/chat-desktop.png`。
- 1440x900 截图人工复核通过：侧栏、顶栏数据源与账号、页面标题、Mock 边界提示、示例问题和底部输入区均完整对齐；控制台 0 error / 0 warning。
- 最终全局规则回归到 768x1024 后仍为上下文 573px、标签 59.95px、选择器 270px，根节点/body 均为 768px，无横向溢出。
- 最终 768x1024 截图已覆盖 `docs/screenshots/chat-tablet.png`，控制台仍为 0 error / 0 warning。
- 最终规则回归到 375x812 后，数据源选择器为 245px、账号按钮为 46px，主内容和输入区均在 375px 内；底部六项导航保留其既有内部横向滚动容器。
- 375x812 底部导航虽不造成页面溢出，但内部 `scrollWidth=398`、`clientWidth=375`，最后一项需滑动；六个标签可在 60px 内完整显示，改为等分并保留 60px 最小宽度。
- 最终移动镜像复验：根节点/body/导航 `scrollWidth` 均为 375，六个导航项各 60.2px，数据源选择器 245px；全部导航无需滑动可见且页面无横向溢出。
- 最终 375x812 截图已覆盖 `docs/screenshots/chat-mobile.png`，控制台为 0 error / 0 warning。
- 最终移动截图人工复核通过：顶栏选择器和账号不重叠，标题/Mock 边界提示/示例问题/输入框完整，底部六项导航文字均可见且无互相遮挡。
- 全新卷 `chatbi-final-smoke` 的 375x812 登录页最终快照通过：固定字号后的品牌/主标题、331px 表单、登录按钮和 Cookie 说明均在视口边界内。
- 最终全新卷实例的管理员真实登录成功，受保护路由跳转到 `/chat`。
- 最终实例默认选择已核验 PostgreSQL 数据源，真实浏览器已填入固定地区订单问句，等待执行终态。
- 最终全新卷 PostgreSQL 固定地区订单问数在真实浏览器进入成功终态并显示“查询返回 5 行”。
- 最终 PostgreSQL 结果显示正确的 `orders JOIN customers WHERE status='paid'` 只读 SQL、LOW 风险、解读最高华东 7；饼图描述逐项为华东 7、华南 6、华北 3、华中 2、西南 2。
- 最终 PostgreSQL 数据表逐行同为华东 7、华南 6、华北 3、华中 2、西南 2，与图表和解读完全一致；最终浏览器控制台 0 error / 0 warning。
- `docs/screenshots/chat-mobile.png` 已由最终全新卷实例的真实固定问数结果覆盖；同一会话切换到 768x1024 继续采集。
- 首次最终平板截图捕获到 `about:blank` 纯白页，已判定无效且必须覆盖；不能沿用该文件作为完成证据。
- 一次性 Playwright 采集已覆盖无效白图：平板/桌面/移动截图分别为 69,173 / 77,532 / 45,604 字节；三视口根节点和 body 均无横向溢出。
- 新平板与桌面截图人工复核为真实结果页：顶栏、导航、问句、解读、LOW 风险 SQL、五区数据表、收藏/导出和输入区均可见，无白屏或遮挡。
- 新移动结果截图人工复核通过：解读、SQL、操作按钮、输入区和六项导航无重叠，关键信息可在内部纵向滚动区访问。
- 已认证状态下重新加载最终 `/chat`：0 console error、0 HTTP >=400、无横向溢出；此前单条 401 仅来自旧 Cookie 首次访问新卷并被登录流程正常恢复。
- 最终自动 E2E 首次捕获到后端图表 JSON 使用 `xfield/yfields/seriesfield` 而前端契约为 camelCase；已在 `ChartRecommendation` 固定为 `xField/yFields/seriesField`，并用 alias 兼容旧快照。
- 修复后图表 JSON 3 项定向测试通过；当前源码重建的 `19034` 后端上，MySQL/PostgreSQL 完整 Mock E2E 均通过图表字段、解读、历史恢复、收藏幂等、XLSX 下载与手写 SQL 断言。

## 真实模型评估发现

- 当前系统环境中的 `OPENAI_API_KEY` 调官方 OpenAI 返回 401，不能把该失败写成模型准确率；报告保留为调用失败基线。
- 可用的真实模型通道是 OpenAI-compatible Responses API；使用 `glm-5.2` 后，原始 26 个模型×方言样本达到 25/26，策略 6/6。
- 原始唯一差异是 PostgreSQL 排名题多返回了一个无害类目列，因此评分器改为参考字段值的一一对应子集比较，同时保持行数相等；错数、漏行、缺字段测试均失败。
- 较早一次扩展集全量为模型 `46/46`、策略 `11/11`；最新全量为模型 `43/46`（93.48%）、澄清 `7/7`、安全拦截 `4/4`，三个门槛仍通过。对外结论只引用最新全量。
- 平均客单价和大区订单意图的定向复跑均为 `2/2`，但定向报告和历史最佳值都不替代最新全量 `43/46`。
- Mock 仅用于确定性流程回归，不计入真实模型准确率。
- 默认 H2 URL 在 H2 2.2 下无法同时使用 `AUTO_SERVER=TRUE` 与 `DB_CLOSE_ON_EXIT=FALSE`，这是实际开箱启动阻断，已删除默认配置中的 `AUTO_SERVER`。

## 2026-08-07 复验补充

- 新漏洞公告使前端审计出现 `brace-expansion` / `postcss` 两个 high；仅更新传递依赖锁至安全版本后，`npm ci`、lint、typecheck、22 项测试、build 和 audit 0 漏洞全部通过。
- MySQL 8.0.46 在本机全新卷初始化约 3 分钟，暴露原 20 秒 `start_period` + 24 次检查会提前标记 unhealthy；改为 240 秒宽限、包装脚本 `--wait` 和 6 分钟 smoke 就绪预算后，第二个全新卷在 204.6 秒一次启动成功。
- 当前源码双库完整 E2E 与 `RealDatabaseSafetyIT` 再次通过；MySQL/PostgreSQL 都证明只读账号、EXPLAIN、固定聚合 `20 / 185551.00` 和数据库写入拒绝。
- 当前真实 Chromium 对 PostgreSQL 固定地区问句再次显示解读、饼图、数据表均为 `7/6/3/2/2`；认证后重载控制台 0 error / 0 warning，所有 API 200，375x812 无页面横向溢出。
- 当前 Docker 性能首轮完成 p95 `938.45ms` 失败并保留；相同参数随后连续两轮为 `676.99ms`、`468.82ms`，全部预算通过。
- `docker-compose.shared.yml` 是不参与默认 Compose 的未跟踪可选覆盖文件，最终范围检查保留且不将其计入默认开箱启动声明。

## 最终已知边界

- v1 强制数据源、表和敏感列权限；未实现行级规则或 SQL 行过滤重写，也不对此作承诺。
- H2 文件元数据适合单 backend 实例；多实例部署需迁移到共享元数据库。
- 真实模型结果存在非确定性；当前最新全量为 `43/46`，部署者应用自己的模型/密钥重跑固定集。
- 外部 LLM 延迟、可用性和配额不计入本地性能结论；已有超时和失败重试边界。
- 前端生产构建仍会报告 `@vueuse/core` 的两条第三方 pure annotation 警告；构建成功，且真实浏览器控制台为 0 error / 0 warning。
- `npm ci` 仍会显示 `whatwg-encoding` 与 `glob@10.5.0` 的传递开发依赖弃用提示；当前 audit 为 0，二者不进入生产运行包。

---
完成态测试命令与实际结果见 `progress.md`；可分发报告见 `reports/README.md`。
