# ChatBI Copilot · 部署说明

Docker Compose 一键演示与生产/预发部署要点。详细问数流程见 [docs/USAGE.md](docs/USAGE.md)。

## 架构概览

```
Browser → frontend:80 (Nginx)
              ↓ /api 反代
         backend:8080 (Spring Boot)
              ↓ JDBC read-only
         目标业务库 (MySQL / PostgreSQL)
              ↓
         H2 卷 backend-data（元数据、历史、语义层）
```

可选 Demo：`mysql:3306` 容器 + 自动导入 `sample-data/mysql/`。

## Project Hub Docker Profile

在 monorepo 内通过 `ai-portfolio/docker` 启动（默认端口 **18182/18183**，避免与 standalone `ai-service-agent` 的 18082/18083 冲突）：

```powershell
cd ai-portfolio/docker
docker compose -f docker-compose.profiles.yml --profile chatbi-copilot up -d --build
curl http://localhost:18182/api/health
```

| 服务 | Hub 默认端口 |
| --- | --- |
| 后端 API | **18182** |
| 前端 UI | **18183** |
| Demo MySQL | 13306 |

验证：`.\verify-all.ps1 -Profile chatbi-copilot`

## 快速部署（演示）

```bash
cp .env.example .env
# 编辑 LLM_API_KEY
docker compose up -d --build
```

| 服务 | 默认地址 | 说明 |
| --- | --- | --- |
| 前端 | http://localhost:8888 | 问数 UI |
| 后端 | http://localhost:8080/api | REST API |
| 健康检查 | http://localhost:8080/api/health | smoke / 负载均衡探活 |
| Swagger | http://localhost:8080/api/swagger-ui.html | API 文档 |
| Demo MySQL | localhost:13306 | 库 `chatbi_demo` |

端口冲突时在 `.env` 设置 `FRONTEND_HOST_PORT`、`BACKEND_HOST_PORT`、`MYSQL_HOST_PORT`。

## 演示账号与示例数据

Docker 默认启用 Demo 数据源（`DEMO_DATASOURCE_ENABLED=true`）。**仅供本地演示，勿用于生产。**

| 项 | 默认值 |
| --- | --- |
| 主机 | `localhost`（容器内为 `mysql`） |
| 端口 | `13306`（映射容器 3306） |
| 数据库 | `chatbi_demo` |
| 用户名 | `chatbi` |
| 密码 | `chatbi123` |
| Root 密码 | `root123` |

应用内会自动注册数据源 **Demo - Sales (MySQL)**（`datasourceId=1`）。示例问句见 [README.md](README.md#-快速开始)。

无 LLM Key 时仍可用 smoke SQL（不调用模型）：

```bash
curl http://localhost:8080/api/health
curl -X POST http://localhost:8080/api/query/run \
  -H "Content-Type: application/json" \
  -d "{\"datasourceId\":1,\"sql\":\"select p.category, count(*) as cnt from products p group by p.category limit 10\"}"
```

（表名以 `sample-data/mysql/01_schema.sql` 为准；若报错请先 `GET /api/datasources` 确认 id。）

## 环境变量（生产）

| 变量 | 说明 |
| --- | --- |
| `LLM_*` | 见 `.env.example` |
| `CHATBI_SECRET` | 数据源密码加密密钥，**生产必改** |
| `DEMO_DATASOURCE_ENABLED` | 生产设为 `false` |
| `SPRING_PROFILES_ACTIVE` | 镜像内 docker profile 已启用 |

后端完整配置：`backend/src/main/resources/application.yml`（支持环境变量覆盖）。

## 生产部署建议

### 1. 拆分 Demo 与真实库

- 生产 compose **移除 `mysql` 服务**或独立网络。
- 通过 UI 或 API 注册真实只读数据源；关闭 Demo 自动注册。

### 2. 持久化

| 卷 | 内容 |
| --- | --- |
| `backend-data` | H2 元库（数据源配置、历史、收藏、语义层） |
| `mysql-data` | 仅 Demo 需要 |

定期备份 `backend-data`；恢复后 `CHATBI_SECRET` 须与备份时一致，否则已存密码无法解密。

### 3. 反向代理

前端 `nginx.conf` 已将 `/api` 代理到 backend。生产在更外层终止 TLS，例如：

```nginx
location / {
  proxy_pass http://chatbi-frontend:80;
}
```

### 4. 健康检查与重启

```bash
curl -sf http://localhost:8080/api/health
docker compose ps
docker compose logs -f backend
```

`docker-compose.yml` 中 MySQL 已配置 `healthcheck`；backend `depends_on` 等待 MySQL 就绪。

### 5. 资源建议（起步）

| 组件 | CPU | 内存 |
| --- | --- | --- |
| backend | 1–2 核 | 1–2 GiB |
| frontend | 0.25 核 | 128 MiB |
| Demo mysql | 0.5 核 | 512 MiB |

LLM 调用为外部 HTTP；并发问数主要消耗后端线程与 LLM 配额。

## 本地开发部署

见 [README.md](README.md#方式二本地开发)。后端 `mvn -s settings.xml spring-boot:run`，前端 `npm run dev`（代理 8080）。

## 升级与回滚

```bash
git pull
docker compose up -d --build
```

回滚：检出上一版本 tag 后重新 build。H2 卷向前兼容由 changelog 保证；重大升级前备份 `backend-data`。

## 压测与性能基线

见 [docs/PERFORMANCE.md](docs/PERFORMANCE.md)（health + `/api/query/run` smoke，**不消耗 LLM**）。

## 相关文档

- [SECURITY.md](SECURITY.md) — 安全与上线清单
- [docs/USAGE.md](docs/USAGE.md) — 使用教程
- [docs/architecture.md](docs/architecture.md) — 架构详图
