# ChatBI Copilot 部署说明

## 环境要求

| 场景 | 要求 |
| --- | --- |
| Windows | Windows 10/11、Docker Desktop、Compose v2 |
| Linux | 64 位 Linux、Docker Engine、Compose v2 |
| 原生源码 | JDK 17+、Maven 3.9+、Node.js 22+、npm 10+ |
| 最低本地资源 | 2 vCPU、4 GiB 可用内存、5 GiB 可用磁盘 |

首次构建需要访问 Docker 镜像仓库、Maven 仓库和 npm registry。默认宿主机端口为 `19030-19033`，本项目所有本地覆盖必须限制在 `19030-19039`。

## 推荐路径：Docker Compose

```bash
cp .env.example .env
docker compose config --quiet
docker compose up -d --build
```

Compose 默认启动：

| 服务 | 宿主机端口 | 容器端口 |
| --- | ---: | ---: |
| backend | `19030` | `8080` |
| frontend | `19031` | `80` |
| MySQL demo | `19032` | `3306` |
| PostgreSQL demo | `19033` | `5432` |

宿主机端口仅使用 `19030-19039`。frontend 通过内部网络把 `/api` 反向代理到 backend。

等待就绪并验收：

```bash
docker compose ps
curl --fail http://127.0.0.1:19030/api/health/ready
node scripts/smoke-mock-demo.mjs http://127.0.0.1:19030
curl --fail http://127.0.0.1:19031/ > /dev/null
```

`smoke-mock-demo.mjs` 会真实登录并依次检查 MySQL 和 PostgreSQL 两个已核验只读数据源。每个库都会执行自然语言问数和手写只读 SQL，断言 `20 / 185551.00` 及五大区 `7 / 6 / 3 / 2 / 2`，并复核图表字段、数据解读、历史快照恢复、收藏幂等、XLSX 响应和收藏删除。任一数据库缺失、可写、错数或闭环不一致都会非零退出。

全新卷在较慢磁盘上初始化 MySQL 可能需要数分钟；Compose 为首次初始化保留 240 秒健康宽限，smoke 默认最多等待 6 分钟。`scripts/demo-mock.ps1` 与 `scripts/demo-mock.sh` 会使用 `docker compose up -d --build --wait` 后再验收。

停止：

```bash
docker compose down
```

删除本地演示数据：

```bash
docker compose down -v
```

`down -v` 会永久删除本地 Compose 卷，仅在明确需要重置演示数据时使用。

## 容器启动顺序

```text
MySQL healthy ─┐
               ├─> backend ready ─> frontend
Postgres healthy┘
```

backend readiness 会检查 H2 元数据库可查询；两个 Demo 数据源在应用初始化时分别执行数据库侧只读授权核验。核验失败时数据源不会变为可查询状态。

## 零密钥与真实模型

`.env.example` 默认：

```dotenv
LLM_PROVIDER=mock
LLM_MODEL=mock
LLM_API_KEY=
```

该模式适合开箱演示和 CI，不代表真实模型效果。接入 Responses API：

```dotenv
LLM_PROVIDER=<provider>
LLM_BASE_URL=https://example.com/v1
LLM_MODEL=<model>
LLM_API_KEY=<secret>
LLM_API_STYLE=responses
LLM_REASONING_EFFORT=none
LLM_TIMEOUT_SECONDS=60
```

Chat Completions 兼容接口使用 `LLM_API_STYLE=chat-completions`。修改后重建或重启 backend：

```bash
docker compose up -d --build backend
```

## 持久化

| 卷 | 内容 |
| --- | --- |
| `backend-data` | H2 元数据、用户、ACL、语义、查询任务、快照、收藏和审计 |
| `mysql-data` | MySQL 演示业务数据 |
| `postgres-data` | PostgreSQL 演示业务数据 |

备份 `backend-data` 时必须同时保留对应 `CHATBI_SECRET`；否则已加密的数据源密码无法解密。H2 文件库只适合单 backend 实例。

## 生产配置

本仓库 Compose 是本地演示基线。生产至少覆盖：

```dotenv
CHATBI_PRODUCTION_MODE=true
CHATBI_SECRET=<random-secret-from-secret-manager>
CHATBI_COOKIE_SECURE=true
CHATBI_ALLOWED_ORIGINS=https://bi.example.com
DEMO_AUTH_ENABLED=false
DEMO_DATASOURCE_ENABLED=false
DEMO_PG_ENABLED=false
LLM_API_KEY=<secret-from-secret-manager>
```

生产启动闸门会拒绝默认加密密钥、演示认证、非 Secure Cookie 或通配 CORS。

还应：

- 在 HTTPS 反向代理后提供 frontend；不把 backend 和数据库端口暴露公网。
- 将 H2 换成适合单实例的受管卷，或在多实例前迁移到共享元数据库。
- 为每个目标库创建独立只读账号并通过 UI 核验。
- 使用外部 Secret Manager 注入 LLM 和数据库凭据。
- 备份元数据库并验证恢复；采集 backend 审计和错误日志。
- 根据实际数据量调整扫描确认、硬阻断、LIMIT、超时和并发预算。

## 原生源码运行

先确认 `java -version`、`mvn -version`、`node --version`、`npm --version` 和 `docker compose version` 满足上方要求。以下命令在 Linux shell 与 Windows `cmd.exe` 均可执行。

目标数据库仍可由 Compose 提供：

```bash
docker compose up -d --wait mysql postgres
```

后端：

```bash
cd backend
mvn -s settings.xml spring-boot:run
```

默认 backend 使用 `19030`，H2 文件为 `backend/data/chatbi.mv.db`。要注册宿主机演示库，设置：

```dotenv
DEMO_DATASOURCE_ENABLED=true
DEMO_DB_HOST=127.0.0.1
DEMO_DB_PORT=19032
DEMO_PG_ENABLED=true
DEMO_PG_HOST=127.0.0.1
DEMO_PG_PORT=19033
```

前端：

```bash
cd frontend
npm ci
npm run dev
```

Vite 固定使用 `127.0.0.1:19031`，并代理到 `127.0.0.1:19030`。

## 升级

1. 备份元数据库卷和 `CHATBI_SECRET`。
2. 运行后端、前端、真实双库和 Compose smoke 门槛。
3. 构建新镜像并启动；Flyway 会在 backend 启动时验证并迁移元数据库。
4. 检查 `/api/health/ready`、登录、数据源只读状态和一条固定问数。

不要在未知的新迁移已写入后直接使用旧镜像回滚。需要回滚时先按备份恢复兼容的元数据库。

## 故障排查

| 现象 | 检查 |
| --- | --- |
| backend 不 ready | `docker compose logs backend`；检查 H2 卷、生产启动闸门和端口 |
| 数据源不可查询 | 打开“数据源”查看只读核验证据；检查只读用户 grant 与网络 |
| 登录 401 | 检查账号是否启用、Cookie 域/HTTPS 和会话是否过期 |
| 写请求 403 | 检查 CSRF 请求头；若为业务动作，再检查角色和数据源能力 |
| 查询需确认 | 查看 EXPLAIN 风险原因；确认后会创建新任务执行 |
| 查询超时/取消 | 查看查询任务和审计；数据库 Statement 会被取消 |
| 真实 LLM 失败 | 检查 base URL、API style、模型名、配额和 60 秒供应商超时 |
| Docker build 拉取失败 | 检查 Docker Hub 网络；不得用旧缓存镜像代替当前源码验收 |

## 相关文档

- [README](README.md)
- [安全说明](SECURITY.md)
- [使用指南](docs/USAGE.md)
- [性能说明](docs/PERFORMANCE.md)
