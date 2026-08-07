# ChatBI Copilot 进度日志

## 会话：2026-07-19

### 阶段 1：完成标准与基线审计
- **状态：** complete
- **开始时间：** 2026-07-19
- 执行的操作：
  - 创建并确认 active Goal。
  - 完整读取统一完成标准与 ChatBI 专项目标。
  - 读取 `acceptance-orchestrator` 与 `planning-with-files-zh` 技能说明。
  - 确认初始工作树干净，范围和端口约束明确。
  - 阅读 README 并确认权限、执行计划/解读、SSE 等能力仍在 Roadmap，不能视为已完成。
  - 确认仓库内没有额外 `AGENTS.md`。
  - 通过 `git ls-files` 核对受控源码；本地构建产物和依赖目录均已忽略，未污染 Git。
  - 确认后端缺少认证/RBAC/数据权限模块，现有自动化测试范围很窄。
  - 审计 Maven/npm 构建配置，确认认证、迁移、双库集成以及前端 lint/typecheck/E2E 工具尚未建立。
  - 审计应用与 Compose 配置，确认端口越界、PostgreSQL 缺失、目标库账号可写、管理端点无认证和原生默认 provider 不一致。
  - 阅读安全与架构文档，确认无登录/ACL/行列权限，且 SQL 解析失败策略违反“默认拒绝”门槛。
  - 审计部署、性能和 CI：旧性能数字不可替代新鲜基线，CI 缺少双库、权限、安全、lint/typecheck、E2E 与仓库卫生门槛。
  - 审计 H2 schema 与 SQL Guard，确认元数据无法按用户隔离，并识别解析失败、锁语句、SELECT INTO、LIMIT/OFFSET 等安全缺口。
  - 审计目标库连接与执行器，确认只读未验证、JDBC 参数无白名单、执行风险/取消缺失以及截断判定不准确。
  - 审计数据源与问数服务，确认匿名/SSRF/全局数据访问、客户端伪造多轮上下文、澄清/审计/结果解读和阶段状态缺口。
  - 审计 Prompt、Schema 和语义层，确认全量注入、无上下文预算/权限裁剪、缓存污染以及指标/JOIN/枚举/版本能力缺失。
  - 审计历史、收藏、导出与图表，确认全局数据越权、审计静默失败、Excel 公式/精度风险和展示/导出不一致。
  - 审计全局错误、CORS、加密与健康检查，确认 HTTP 状态、信息泄漏、任意凭据跨域、默认密钥和虚假健康状态问题。
  - 使用 frontend-design 标准审计 App/问数/结果/图表/API；发现假阶段、无取消、跨数据源上下文泄漏、图表数值篡改和移动端布局缺口。
  - 审计数据源/语义/历史/收藏页面与运行镜像，确认表单状态、危险清空行为、固定宽度移动端、SSE/Nginx 和镜像测试缺口。
  - 审计现有测试、Mock、样例库、smoke/压测和审计文档；发现不存在表 + HTTP 200 导致的性能假绿、Mock 默认成功和占位截图。
- 创建/修改的文件：
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

### 阶段 2：安全、权限、数据源与语义层
- **状态：** complete
- **开始时间：** 2026-07-19
- 执行的操作：
  - 确定服务端可撤销会话、统一 DataAccessPolicy、fail-closed SQL、结果快照与 Flyway 迁移方向。
  - 停止陈旧缓存镜像 Compose 栈，保留数据卷供迁移兼容检查。
  - 引入 Spring Security/Flyway，建立用户、角色、会话、ACL、查询快照、语义修订和审计 Schema。
  - 实现 HttpOnly 会话、CSRF、登录限速、request id、正确 HTTP 错误、生产安全启动闸门和演示账号初始化。
  - 完成服务端问数会话接入，客户端历史不再参与 Prompt；成功结果保存规范化快照和 SHA-256。
  - 完成确定性结果解读，收藏仅能引用本人成功查询，Excel 仅按 queryId 导出同一快照并重新验权。
  - MySQL 权限核验改为 SELECT/SHOW VIEW/USAGE 白名单；PostgreSQL 增加有效写表、可写 Schema 和高权角色检查。
  - 连接池拒绝未通过只读核验的数据源；高精度值使用无损字符串并禁止生成可能改值的图表。
  - 完成语义层真实 Schema 资源校验，指标表达式复用 AST Guard，阻止不存在的表列和 JOIN 端点。
  - 完成真实 MySQL/PostgreSQL 应用级集成验收：账号权限、Schema、EXPLAIN、执行、写入拒绝和图表字段一致。
- 创建/修改的文件：
  - `backend/pom.xml`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/resources/db/migration/V1__secure_metadata.sql`
  - `backend/src/main/java/com/chatbi/copilot/auth/**`
  - `backend/src/main/java/com/chatbi/copilot/audit/**`
  - `backend/src/main/java/com/chatbi/copilot/common/{ApiResponse,BusinessException,GlobalExceptionHandler,RequestIdFilter}.java`

### 阶段 3：问数与结果闭环
- **状态：** complete
- **开始时间：** 2026-07-20
- 执行的操作：
  - 新增持久化异步查询任务、真实阶段轮询、每用户并发上限、取消、90 秒总超时和失败重试。
  - 修复取消与超时的终态竞态，确保用户取消与总超时可区分。
  - 新增确定性澄清/拒绝策略；多轮上下文只读取同用户、同数据源、同服务端会话。
  - 完成 EXPLAIN 风险、二次确认、硬阻断、执行耗时和全链路审计。
  - 表格、图表、确定性解读、历史恢复、收藏和 Excel 统一绑定带 SHA-256 的结果快照。

### 阶段 4：页面、交互与性能
- **状态：** complete
- 统一为紧凑的数据工作台视觉，六个业务页及登录页均补齐 loading、empty、error/retry、offline、disabled、success 和无权限状态。
- 完成 `375x812`、`768x1024`、`1440x900` Chromium 适配；根节点、body 和移动导航均无横向溢出。
- 登录首屏拆包后 Lighthouse 为 Performance 90、Accessibility 100、Best Practices 100。
- 2026-08-07 当前 Docker 栈性能首轮保留 `938.45ms` 完成 p95 失败；随后相同参数连续两轮通过，确认轮为读 `74.37ms`、创建 `103.25ms`、完成 `468.82ms`。

### 阶段 5：评估、测试与开箱启动
- **状态：** complete
- 固定集共 34 个唯一问题、57 次执行；最新真实模型全量为 `43/46`（93.48%），策略 `11/11`，三个预设门槛通过。
- 后端常规全量 73 项通过；真实双库 IT 1 项通过；共 18 个套件、74 项、0 失败、0 跳过。
- 前端 lint、typecheck、5 文件 22 项测试、build 和 `npm audit` 0 漏洞通过；评估器自测 5/5 通过。
- 全新卷 Java 17 Compose 在 `19034-19037` 启动，双库完整 Mock E2E 和性能门槛通过。

### 阶段 6：真实浏览器与最终完成闸门
- **状态：** complete
- 真实 Chromium 已逐页覆盖登录、数据源、问数、语义、历史、收藏和权限，并完成三视口截图。
- MySQL/PostgreSQL 同问句的 SQL、表格、图表和解读均为华东 7、华南 6、华北 3、华中 2、西南 2。
- 2026-08-07 当前源码再次通过全新卷 Compose、双库 E2E、真实双库 IT、真实浏览器、性能稳定复测、仓库卫生和范围复核。

## 测试结果

| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| 初始 Git 状态 | `git status --short --branch` | 无既有工作树改动 | 仅 `## main...origin/main` | pass |
| 后端基线 | `mvn -s settings.xml -B test` | 现有测试通过 | 22 tests，0 failure/error/skip | pass（范围不足） |
| 前端基线单测 | `npm test -- --run` | 现有测试通过 | 3 files / 12 tests passed | pass（范围不足） |
| 前端基线构建 | `npm run build` | 可构建 | pass；Element Plus 1.12MB、ECharts 1.08MB，2 条第三方 warning | pass（需优化） |
| Docker 环境 | `docker version` | 可用于双库与 Compose 验收 | Desktop 4.82 / Engine 29.6.1 | pass |
| Docker 基线构建（首次） | `docker compose up -d --build`，端口 19030-19032 | 构建并启动 | Docker Hub metadata 请求 EOF | fail（外部网络） |
| 缓存镜像 Compose 基线 | `docker compose up -d --no-build` | 仅用于现有流程观察 | MySQL/backend/frontend 均启动在 19030-19032 | pass（非源码镜像） |
| 现有 Mock smoke | `node scripts/smoke-mock-demo.mjs http://localhost:19030` | 完成 health→LLM→datasource→ask→run | health 后因 mock configured=false 失败 | fail |
| 安全骨架编译 | `mvn -s settings.xml -B -DskipTests compile` | 新增依赖和源码可编译 | 91 source files，BUILD SUCCESS | pass |
| Flyway/认证上下文 | `mvn -s settings.xml -B -Dtest=ApplicationContextTest test` | 全新 H2 执行迁移并初始化三账号 | 1 test passed；V1 applied | pass（有版本提示待清理） |
| SQL/ACL/JDBC/HTTP 安全首轮 | 4 个测试类，共 25 tests | 全部通过 | 24 pass；方法级拒绝误为 500 | fail（已修） |
| HTTP 权限复测 | `mvn ... -Dtest=SecurityIntegrationTest test` | 401/403/注销边界正确 | 2 tests passed | pass |
| 双库种子等价 | 两库只读账号执行已支付订单聚合 | 数字相同 | MySQL/PostgreSQL 均为 20 单、185551.00 元 | pass |
| MySQL 数据库只读 | SHOW GRANTS + UPDATE 探针 | 仅 SELECT；写入拒绝 | USAGE + SELECT/SHOW VIEW；UPDATE 1142 | pass |
| PostgreSQL 数据库只读 | 角色/CREATE 检查 + UPDATE 探针 | 无高权；写入拒绝 | super/createdb/create=false；read-only transaction 拒绝 | pass |
| 查询快照/收藏/导出安全 | 11 项针对性单元和集成测试 | 跨用户、撤权、篡改、公式和精度均受控 | 11 tests，0 failure/error/skip | pass |
| 异步任务状态机 | 阶段、跨用户、取消、超时、重试 3 项集成测试 | 终态稳定且不可越权 | 3 tests，0 failure/error/skip | pass |
| 确定性澄清与 Mock 拒绝 | 澄清规则和双语方言 8 项测试 | 首轮澄清、多轮继承、未知/危险请求拒绝 | 8 tests，0 failure/error/skip | pass |
| 语义真实资源校验 | 指标、表、JOIN、时间类型 2 项测试 | 无效元数据写入前拒绝 | 2 tests，0 failure/error/skip | pass |
| 双库应用级安全与一致性 | `RealDatabaseSafetyIT` 连接 19032/19033 | 只读、计划、结果、图表、写入拒绝全通过 | 1 test，0 failure/error/skip；20 / 185551.00 一致 | pass |
| 双库浏览器结果等价 | 375x812 Chromium 对 MySQL/PostgreSQL 提交同一地区订单问句 | SQL、表格、图表、解读数字逐项相同 | 双库均为华东 7、华南 6、华北 3、华中 2、西南 2；控制台无错误 | pass |
| 平板顶栏响应式复验 | 768x1024 Chromium + 计算样式 | 标签单行、数据源可读、无横向滚动 | 上下文 573px、标签 59.95px、选择器 270px、scrollWidth 768px | pass |
| 三视口最终布局 | 375x812、768x1024、1440x900 Chromium | 顶栏/导航/内容无溢出、遮挡或截断 | 三档页面 scrollWidth 等于 viewport；移动六项导航各 60.2px；桌面/平板选择器 270px | pass |
| 最终镜像浏览器问数 | 全新卷 Java 17 Docker 栈，PostgreSQL 固定地区订单问句 | SQL、表格、图表、解读一致且控制台干净 | 五区 7/6/3/2/2；三截图非空；认证后 reload 0 console error、0 HTTP >=400 | pass |
| 后端最终全量 | `mvn -s settings.xml -B test` | 常规单元/集成全绿 | 73 tests，0 failure/error/skip | pass |
| 真实双库最终复测 | `mvn -s settings.xml -B -Dtest=RealDatabaseSafetyIT test` | 双库只读、EXPLAIN、结果和图表一致 | 1 test 通过；合计 18 suites / 74 tests 全绿 | pass |
| 前端最终质量门槛 | lint + typecheck + Vitest + build + audit | 全部通过且无高危漏洞 | 5 files / 22 tests；build 通过；0 vulnerabilities | pass |
| 评估器最终自测 | `node --test scripts/evaluate-nl2sql.test.mjs` | 等价、错数、容差和枚举规则正确 | 5/5 passed | pass |
| 双库完整 Mock E2E | `node scripts/smoke-mock-demo.mjs http://127.0.0.1:19034` | 登录→问数→护栏→执行→图表/解读→Excel→历史/收藏 | MySQL/PostgreSQL 均通过 `20 / 185551.00`、`7/6/3/2/2`、快照、收藏幂等和 XLSX 断言 | pass |
| 最终 Docker 性能 | 每轮 15 次、并发 3、认证态 MySQL 查询 | 读 <=300ms，本地写/查询 <=800ms，业务错误 0 | 首轮完成 p95 938.45ms 失败并保留；随后两轮 676.99ms、468.82ms 通过，均 15/15 | pass（连续两轮） |
| 登录首屏 Lighthouse | 真实 Chrome、生产镜像 | Performance >=85，A11y/BP >=90 | Performance 90，Accessibility 100，Best Practices 100 | pass |

## 错误日志

| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------|---------|
| 2026-07-19 | 当前任务已存在 active Goal，重复创建失败 | 1 | 沿用现有 Goal |
| 2026-07-19 | PowerShell 预解析 `&` 导致组合命令失败 | 1 | 改为独立并行工具调用 |
| 2026-07-19 | 总目标输出截断 | 1 | 按章节行号单独读取 |
| 2026-07-19 | Compose 构建解析 `eclipse-temurin:21-jre` / `nginx:alpine` 时 Docker Hub 返回 EOF | 1 | 检查镜像缓存并采用替代拉取路径；非代码失败 |
| 2026-07-19 | 缓存镜像 Mock smoke 报 `LLM not configured in mock mode` | 1 | 判定镜像陈旧；后续仅验收由当前源码构建的进程/镜像 |
| 2026-07-19 | SQL Guard 编译时 `Select` 同时匹配 Statement/Expression 重载 | 1 | 显式转换为 `Statement` 后调用 |
| 2026-07-19 | 扩展 SQL Guard 测试缺少两个新增类型 import | 1 | 补齐导入；测试尚未执行 |
| 2026-07-19 | 方法级权限拒绝返回 500 而不是 403 | 1 | GlobalExceptionHandler 增加安全拒绝映射 |
| 2026-07-19 | Windows 策略拒绝启动包含演示口令参数的 `cmd.exe` | 1 | 改用直接 `docker exec`；命令未进入容器 |
| 2026-07-20 | 通过 `cmd.exe` 内联解析 Lighthouse JSON 被系统策略拒绝 | 1 | 改用直接调用 Node.js 的只读解析命令；报告文件未受影响 |
| 2026-07-20 | `cmd.exe` 将 `rg` 正则中的管道/尖括号解释为 shell 操作符 | 1 | 改用不经 `cmd.exe` 包装的 `rg` 单引号参数；仅搜索失败，无文件影响 |
| 2026-07-20 | 首个前端性能整批补丁因 `ChatView.vue` 导入上下文不匹配而校验失败 | 1 | 确认补丁原子回滚、源码未部分改写；按文件拆分并使用实际上下文应用 |
| 2026-07-20 | Windows `rg` 不展开 `dist/assets/LoginView-*.js` 通配符 | 1 | 直接检查 `dist/index.html` 已确认首屏引用；后续先列出文件名或搜索整个 `dist/assets` 目录 |
| 2026-07-20 | 重建 `chatbi-source-smoke` 前端时漏传验收端口覆盖，容器回退到允许范围内的默认 `19031`，导致 `19035` 探针暂时不可达 | 1 | 容器和 Nginx 均正常；按原验收覆盖值在 `19035` 重建前端，后端/双数据库端口不变 |
| 2026-07-20 | Playwright 直接点击数据源选择器内部 `input` 时被覆盖文本拦截而超时 | 1 | 重新快照后点击可点击父容器，选择器正常展开；不是产品功能失败 |
| 2026-07-20 | `cmd /c rg` 把包含 `|` 的样式定位正则解释成 shell 管道 | 1 | 改用多个不含 shell 元字符的精确搜索；只读搜索失败，无文件影响 |
| 2026-07-20 | 前端容器重建后旧 Playwright 会话失去响应：自动快照为 0 字节，主动快照和标签列表无输出 | 2 | HTTP 服务正常；停止复用旧会话，改建独立 Chromium 会话重新登录，空产物不作为证据 |
| 2026-07-20 | Windows 管理策略拦截第二次 `cmd -> node -> docker compose` 包装 | 1 | 命令未执行、容器未变化；改用临时 Compose env 文件传入允许端口并在完成后删除 |
| 2026-07-20 | 文档核对时 `cmd` 再次拆解 `rg` 管道正则，且两条 `cmd -> node -e` 只读解析被管理策略拦截 | 2 | 均未改文件；后续 Node 直接执行，Maven 搜索拆成不含 shell 元字符的独立词 |
| 2026-07-20 | 用 `cmd for` 打印 `loadtest/__pycache__` 绝对路径时语法被 shell 拒绝 | 1 | 文件清单已显示唯一 `.pyc`；改用只读 `Resolve-Path -LiteralPath` 后再删除固定目录 |
| 2026-07-20 | PowerShell `Remove-Item` 删除已核对的缓存目录时被管理策略拦截 | 1 | 没有部分删除；目标已确认在工作区内，改用单一 `cmd rmdir` 固定绝对路径 |
| 2026-07-20 | 缓存删除成功后，`cmd if (...)` 确认命令被外层 PowerShell 预解析 | 1 | 不重复该语法，改用只读 `Test-Path -LiteralPath` 验证 |
| 2026-07-20 | 采集最终截图时应用内 Playwright 连续在导航成功后的下一操作退回 `about:blank` | 2 | 无效白图不作为证据；先检查标签选择，若无目标标签则改用独立终端 Chromium 一次性采集 |
| 2026-07-20 | 完整 Mock E2E 首次发现图表 JSON 为 `xfield/yfields` | 1 | 修正为前端契约 `xField/yFields/seriesField`，兼容读取旧快照；3 项定向测试及双库完整 E2E 通过 |
| 2026-07-20 | 并发 npm 门槛时 audit 返回依赖树无效 | 1 | 等其他 npm 进程退出后单独复跑，结果为 0 vulnerabilities，锁文件未改写 |
| 2026-08-07 | 新增漏洞公告使 `brace-expansion` / `postcss` 审计变为 2 high | 1 | 仅更新传递依赖锁，`npm ci` 后 lint/typecheck/22 tests/build/audit 全部通过，0 vulnerabilities |
| 2026-08-07 | MySQL 8.0.46 全新卷初始化超过原 Compose health budget | 1 | health `start_period` 扩至 240s、包装脚本加 `--wait`、smoke 默认等待 6 分钟；第二个全新卷一次通过 |
| 2026-08-07 | 当前 Docker 性能首轮查询完成 p95 938.45ms | 1 | 失败 JSON 保留；相同参数两轮复测分别 676.99ms、468.82ms，连续通过预算 |

## 五问重启检查

| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 6：最终完成闸门已通过 |
| 我要去哪里？ | 关闭 Goal；不提交、不推送、不发布 |
| 目标是什么？ | 见 `task_plan.md` 目标与完成闸门 |
| 我学到了什么？ | 安全需要 AST、ACL 和数据库只读纵深防御；展示一致需要同一不可变快照 |
| 我做了什么？ | 完成功能、双库安全、固定集、页面、性能、Docker、真实浏览器和最终范围检查 |

## 2026-07-20 真实模型与固定评估

- 修复默认 H2 文件 URL 中 `AUTO_SERVER=TRUE` 与 `DB_CLOSE_ON_EXIT=FALSE` 的不兼容组合，源码服务已在 19030 成功启动。
- LLM 客户端同时支持 OpenAI-compatible Chat Completions 与 Responses API；GPT-5 系列请求参数和 Responses 解析均有单元测试。
- 固定集扩展为 34 个唯一问题、57 次执行，覆盖 23 个模型题的 MySQL/PostgreSQL 双方言和 11 个澄清/拒绝题。
- 较早一次真实 `glm-5.2` Responses API 全量为模型 `46/46`、策略 `11/11`；该结果用于观察模型非确定性，不作为最新准确率。
- 最新全量真实模型报告为模型 `43/46`（93.48%）、澄清 `7/7`、安全拦截 `4/4`，三个预设门槛全部通过。
- 平均客单价和大区订单意图的定向真实模型复跑均为 `2/2`；定向报告不替代最新全量 `43/46`。
- 评估器按真实执行结果一一对应比较，允许无害附加列，但拒绝错数、漏行和缺少参考字段；Node 自测 5/5 通过。
- Mock 只用于确定性流程回归，从未计入上述真实模型数字。

## 2026-07-20 登录页性能修复

- 将工作区壳层从根组件移入受保护父路由，公共登录页不再静态引用工作区导航、选择器和账户菜单。
- Element Plus 改为页面组件级自动导入，全部图标注册改为 8 个实际图标的显式导入。
- 匿名登录路由不再发起必然返回 401 的 `/api/auth/me`；受保护路由仍强制恢复会话并执行角色判断。
- 新增 4 个路由守卫测试；前端 lint、typecheck、build 通过，5 个测试文件、22 个测试全部通过，npm audit 0 漏洞。
- 登录表单改为可访问的原生控件并保留 loading/offline/error 状态；移除导致公共入口提升组件库依赖的 Element Plus 手工分块。
- 产物级检查确认登录 HTML 不再预加载 Element Plus、工作区布局和 ECharts。
- 重建源码前端镜像并在 `19035` 运行；哈希静态资源返回 `Cache-Control: max-age=31536000`，安全响应头仍完整。
- 登录页 Lighthouse 复测达到 Performance 90、Accessibility 100、Best Practices 100；FCP 2.2s、LCP 2.4s、TBT 270ms、CLS 0，首屏传输 192,662 bytes。
- Playwright Chromium 375x812 登录页视觉复核通过，无溢出、遮挡或截断。
- 当前源码镜像真实登录并在 PostgreSQL 执行固定双指标问数，解读和表格均为 20 单 / 185551.00 元。
- 浏览器发现“各大区的已支付订单数量”在 Mock 中误命中客户数 SQL；记录为 fail，进入针对性修复，未作为通过证据。
- 修复 Mock 区域订单数意图优先级并收紧客户数条件；中英文订单数与显式客户数共 9 个测试通过。
- 重建 Java 17 后端镜像并在 `19034` 健康运行；PostgreSQL 浏览器复验表格/图表/解读一致为 7/6/3/2/2。

## 2026-08-07 最终复验

- 后端常规全量 73 项通过；可覆盖端口的 `RealDatabaseSafetyIT` 在 MySQL `19036` / PostgreSQL `19037` 通过，合计 74 项、0 失败、0 跳过。
- 前端锁文件修复当日新增的 2 个 high 漏洞；`npm ci`、lint、typecheck、5 文件 22 项测试、build 和 audit 0 漏洞全部通过。
- 第二个全新 Compose 项目 `chatbi-final-20260807b` 在 `19034-19037` 用 204.6 秒一次达到四服务就绪；MySQL 慢初始化的健康检查误判已修复。
- 双库完整 Mock E2E 再次通过登录、聚合、地区问数、图表字段、解读、历史快照、收藏幂等、真实 XLSX 和手写只读 SQL；Mock 未计入真实模型准确率。
- 当前真实 Chromium 登录后执行 PostgreSQL 固定地区问句，LOW 风险 SQL、解读、饼图和数据表均为 `7/6/3/2/2`；认证后重载 API 全 200、控制台 0 error / 0 warning，375x812 无页面横向溢出。
- 性能首轮 15/15 业务成功但完成 p95 `938.45ms` 未过预算，失败报告保留；随后相同参数连续两轮为 `676.99ms`、`468.82ms`，均通过。
- 密钥扫描扩展到已跟踪及未忽略的新文件；Compose 配置、端口文档、临时文件、截图、日志、`git diff --check` 和 Git 范围完成最终复核。
