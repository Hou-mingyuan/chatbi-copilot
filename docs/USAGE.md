# 使用指南 · Docker 问数流程

本文档说明如何用 Docker Compose 一键启动 ChatBI Copilot，并完成从配置 LLM 到自然语言问数的完整流程。

## 前置条件

- 已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（含 Docker Compose）
- 拥有一个 OpenAI 兼容的 LLM API Key（推荐 DeepSeek，也可换 OpenAI / 通义 / 本地 Ollama）
- 本机端口 **13306**、**8080**、**8888** 未被占用；如被占用，可用 `.env` 中的 `MYSQL_HOST_PORT`、`BACKEND_HOST_PORT`、`FRONTEND_HOST_PORT` 覆盖

## 第一步：准备环境变量

在项目根目录执行：

```bash
cd chatbi-copilot
cp .env.example .env
```

编辑 `.env`，至少填入你的 LLM Key：

```env
LLM_API_KEY=sk-your-real-key-here
```

其他常用配置（可选）：

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `LLM_PROVIDER` | 供应商标识（展示用） | `deepseek` |
| `LLM_BASE_URL` | OpenAI 兼容 base url | `https://api.deepseek.com/v1` |
| `LLM_MODEL` | 模型名 | `deepseek-chat` |
| `LLM_TEMPERATURE` | 采样温度，SQL 生成建议 0 | `0.0` |
| `MYSQL_HOST_PORT` | 示例 MySQL 暴露到宿主机的端口 | `13306` |
| `BACKEND_HOST_PORT` | 后端 API 暴露到宿主机的端口 | `8080` |
| `FRONTEND_HOST_PORT` | 前端页面暴露到宿主机的端口 | `8888` |

> **注意**：`.env` 含敏感信息，请勿提交到 Git。

## 第二步：一键启动

```bash
docker compose up -d --build
```

首次启动会：

1. 构建并启动 **MySQL 8** 容器，自动导入 `sample-data/mysql/` 建表与种子数据
2. 构建并启动 **后端**（Spring Boot），自动注册指向示例库的 Demo 数据源
3. 构建并启动 **前端**（Nginx 托管 Vue 静态资源）

查看启动状态：

```bash
docker compose ps
docker compose logs -f backend   # 后端就绪后可 Ctrl+C 退出
```

## 第三步：打开应用

| 入口 | 地址 |
| --- | --- |
| **前端（问数界面）** | http://localhost:8888 |
| **后端健康检查** | http://localhost:8080/api/health |
| **Swagger API 文档** | http://localhost:8080/api/swagger-ui.html |
| **H2 控制台（元数据）** | http://localhost:8080/api/h2-console |

若使用自定义端口，例如：

```env
FRONTEND_HOST_PORT=18888
BACKEND_HOST_PORT=18084
MYSQL_HOST_PORT=13316
```

则前端改为 `http://localhost:18888`，健康检查改为 `http://localhost:18084/api/health`。

## 第四步：确认 LLM 已配置

1. 打开前端 http://localhost:8888
2. 顶部应显示当前数据源为 **Demo - Sales (MySQL)**
3. 若 LLM Key 未配置，问数页会提示「请先配置 LLM」——检查 `.env` 中 `LLM_API_KEY` 后执行：

```bash
docker compose up -d --force-recreate backend
```

也可调用 API 检查：

```bash
curl http://localhost:8080/api/llm/status
```

没有真实 LLM Key 时，仍可用 Docker smoke 验证应用、Demo 数据源与 SQL 护栏：

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/datasources
curl -X POST http://localhost:8080/api/query/run ^
  -H "Content-Type: application/json" ^
  -d "{\"datasourceId\":1,\"sql\":\"select category, sum(amount) as total_amount from sales_orders group by category order by total_amount desc\"}"
```

自然语言问数 `/api/query/ask` 需要真实 LLM Key；`/api/query/run` 不调用 LLM，可用于本地和 CI smoke。

## 第五步：开始问数

在首页输入框中用自然语言提问，例如：

- `各产品类目的销售额占比`
- `2024年每月销售额趋势`
- `销售额最高的5个产品`
- `各大区的客户数量`

系统会依次：

1. 读取当前数据源的表结构与字段注释
2. 叠加语义层配置（如有）
3. 调用 LLM 生成 SQL
4. 经 **SqlGuard** 只读护栏校验（仅 SELECT + LIMIT）
5. 在示例库执行并返回结果
6. 自动推荐柱状/折线/饼图，可手动切换

### 多轮追问

在同一会话中继续输入，例如：

- 第一问：`2024年每月销售额趋势`
- 追问：`只看华东大区`
- 追问：`改成按季度汇总`

历史上下文会自动带入，无需重复描述。

## 第六步：其他常用功能

### 数据源管理

路径：**数据源** 菜单

- 查看 Demo 数据源连接信息
- 新增自己的 MySQL / PostgreSQL 业务库
- 点击「测试连接」验证后再问数

### 语义层

路径：**语义层** 菜单

为表/字段添加业务别名，例如：

| 表 | 字段 | 业务别名 | 描述 |
| --- | --- | --- | --- |
| orders | status | 订单状态 | paid=已支付, cancelled=已取消 |

配置后 LLM 更容易理解业务口径。

### 查询历史与收藏

- **历史**：查看过往问数记录，支持重跑
- **收藏**：保存常用问句，一键再次提问

### 导出 Excel

问数结果页点击「导出 Excel」，下载当前表格数据。

## 停止与清理

```bash
# 停止服务（保留数据卷）
docker compose down

# 停止并删除 MySQL / 元数据卷（彻底重置）
docker compose down -v
```

## 常见问题

### 问数报错「LLM 未配置」

确认 `.env` 中 `LLM_API_KEY` 已填写，并重建 backend 容器。

### 问数报错「SQL 被安全护栏拒绝」

系统仅允许 **只读 SELECT**。若问题涉及写操作，请改问法或检查 LLM 是否生成了 DML/DDL。

### MySQL 连接失败

等待 MySQL 健康检查通过后再访问后端：

```bash
docker compose logs mysql
```

如果宿主机 3306 已有本地 MySQL，不需要关闭它；默认 Compose 使用 `13306:3306`。

### 换用 Ollama 本地模型

`.env` 示例：

```env
LLM_PROVIDER=ollama
LLM_BASE_URL=http://host.docker.internal:11434/v1
LLM_MODEL=llama3.1
LLM_API_KEY=
```

## 下一步

- 架构细节见 [architecture.md](architecture.md)
- API 完整列表见 README 或 Swagger UI
- 本地开发（非 Docker）见根目录 [README.md](../README.md)「方式二：本地开发」
