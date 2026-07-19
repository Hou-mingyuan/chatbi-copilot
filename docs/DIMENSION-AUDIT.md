# ChatBI Copilot · 十维审计报告（Round-6）

> **审计日期**：2026-07-06（**Round-6 十维复测** · project-hub-1 · k6 50 VU 复跑确认）  
> **范围**：`chatbi-copilot`（Spring Boot backend · Vue frontend · MySQL demo · docs）  
> **评分**：1–10 分  
> **关联**：[PRODUCTION-READINESS.md](../../ai-portfolio/PRODUCTION-READINESS.md) · [PERFORMANCE_REPORT.md](../PERFORMANCE_REPORT.md)

---

## 总览

| 维度 | 得分 | 等级 |
| --- | ---: | --- |
| 1. 文档与 README | **8** | 良好 |
| 2. Docker 与部署 | **8** | 良好 |
| 3. CI / CD | **9** | 优秀 |
| 4. 性能与压测 | **9** | 优秀（k6 50 VU 实测 ✅ P95 79.7ms） |
| 5. 安全基线 | **8** | 良好 |
| 6. 测试与质量 | **8** | 良好 |
| 7. API 与架构 | **9** | 优秀 |
| 8. 前端 UX | **9** | 优秀（ask 流式打字机 + 图表钻取 ✓） |
| 9. 演示与作品集 | **9** | 优秀 |
| 10. 可维护性与工程化 | **8** | 良好 |
| **加权平均** | **8.5** | **作品集就绪+** |

**结论**：**Text2SQL + SqlGuard 只读护栏**是核心差异化；Mock `/api/query/run` 零 LLM 可验收。**Round-7**：ask 流式打字机 + 图表钻取 + Vitest 12/12 绿（project-hub-2）；均分 **8.5**（D8 8→9）。

---

## 1. 文档与 README（8/10）

### 现状

- README：Docker 一键、端口覆盖、Demo 数据源说明。
- `docs/USAGE.md`、`DEPLOYMENT.md`、`SECURITY.md`、`docs/PERFORMANCE.md`。
- Hub Profile 端口 **18182/18183** 与 verify 记录。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | ~~README 增加 **问数流程 sequenceDiagram**~~ → README §问数全链路时序 ✅ |
| P2 | Mock 四步演示与 `ResultPanel` 三 Tab 截图占位 |
| P3 | 中英文 README 对齐（若需国际化作品集） |

---

## 2. Docker 与部署（8/10）

### 现状

- MySQL + backend + frontend 三件套；`MYSQL_HOST_PORT` / `BACKEND_HOST_PORT` / `FRONTEND_HOST_PORT` 可覆盖。
- Demo 库 utf8mb4 中文种子已修复。
- `LLM configured` 状态探测准确（无 Key 时 `configured=false`）。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | 生产拓扑：托管 MySQL + 只读从库连接示例 |
| P3 | HTTPS 反代 + WebSocket（若后续加协作功能）模板 |

---

## 3. CI / CD（9/10）

### 现状

- `.github/workflows/ci.yml` 远程 **GHA 绿** + README badge（P4 批次）。
- 后端 `mvn test`、前端 build + audit 0（Vite 8 升级后）。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | CI 增加 `docker compose config` + health curl smoke |
| ~~P2~~ | ~~CI compose smoke job~~ → 已用 `smoke-mock-demo.mjs` 统一 |
| P3 | k6 smoke nightly job |

---

## 4. 性能与压测（9/10）

### 现状

- `performance/k6-smoke.js`：health + datasources + **query/run**（无 LLM）。
- **k6 50 VU 实测**（2026-07-06 · Hub `:18182`）：P95 **121.39 ms** · RPS **120.5** · 错误率 **0%** — Round-5 首测。
- **Round-6 复跑**（project-hub-1 · 50 VU × 30s）：P95 **79.7 ms** · RPS **134.4** · 1380 iter · 0% 失败 — 基线稳定。
- 结果已写入 [PERFORMANCE_REPORT.md](../PERFORMANCE_REPORT.md) §k6 50 VU 复测。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| ~~**P1**~~ | ~~k6 50 VU 实测回填~~ → ✅ 2026-07-06 |
| P2 | `/api/query/ask` 独立 soak（有 Key 环境） |
| P3 | 连接池/线程池可配置化（Roadmap 已列） |

---

## 5. 安全基线（8/10）

### 现状

- **JSqlParser SqlGuard**：拦截 DML/DDL/多语句/危险函数，强制 LIMIT。
- 目标库 JDBC **readOnly=true**，连接池 max 5。
- `SECURITY.md`：密钥、生产基线、漏洞报告。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | ~~应用层认证 + API 限流（Roadmap）~~ → [SECURITY.md §应用层认证](../SECURITY.md#应用层认证与限流-roadmap首期--p2) ✅ |
| P3 | 数据源密码 AES 轮换与审计日志 |
| P3 | SQL 注入 fuzz 回归集 |

---

## 6. 测试与质量（8/10）

### 现状

- 后端 **18** 测试：SqlGuard 放行/拦截/LIMIT/CTE/多语句、PromptBuilder、LLM 配置判断。
- 前端 build + npm audit 0。
- 前端 Vitest：`ResultPanel` 三 Tab / SQL 折叠预览 / 元数据标签单测（`npm test`）。
- CI `docker-smoke` job 使用 `scripts/smoke-mock-demo.mjs` 统一验收（health / mock LLM / datasources / ask / run）。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P3 | 集成测试：Demo 数据源 query/run 全链路 |

---

## 7. API 与架构（9/10）

### 现状

- Schema 感知 Text2SQL + 语义层别名 + 图表推荐（pie/bar/line）。
- 多数据源管理、查询历史、H2 元数据 + MySQL 目标库分离。
- OpenAI 兼容 LLM 可插拔。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P3 | 多轮澄清对话状态机文档 |
| P3 | 大结果集导出异步化 |

---

## 8. 前端 UX（9/10）

### 现状

- **P2 批次**：`ResultPanel` **三 Tab**（图表 / 数据 / SQL）；SQL **折叠预览**；数据卡片。
- **Round-7**：ask **三阶段加载文案** + 结果 **打字机 UX** + 失败 **重试**；图表 **点击钻取** 同步筛选表格（project-hub-2）。
- **`defineAsyncComponent` 懒加载** + `npm run build:analyze`。
- LLM 未配置时友好提示 + 示例问题。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| ~~P2~~ | ~~图表交互（钻取/导出）与空状态统一~~ ✅ Round-7 钻取 |
| ~~P3~~ | ~~自然语言 ask 流式打字机 UX~~ ✅ Round-7 |
| P3 | 深色模式、移动端表格横向滚动优化 |

---

## 9. 演示与作品集（9/10）

### 现状

- Mock **零密钥** `POST /api/query/run` 验证护栏 + 中文 Demo 数据。
- Hub Profile **18182/18183** verify；CSDN 正文就绪。
- 前端 Mock 一键 4 步演示（README / USAGE）。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | `scripts/demo-mock.ps1` 一键（若尚未统一命名） |
| P3 | 录屏：示例问题 → SQL → 饼图 |

---

## 10. 可维护性与工程化（8/10）

### 现状

- CHANGELOG、VERSION、Vite 8 / ECharts 6 升级完成。
- 端口与环境变量文档化，与 ai-portfolio 矩阵一致。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | 根脚本 `test-all`（mvn + npm） |
| P3 | OpenAPI 导出与前端类型同步 |

---

## 优先行动清单（Top 7）

| # | 优先级 | 动作 | 维度 |
| ---: | ---: | --- | --- |
| 1 | ~~**P1**~~ | ~~k6 50 VU 实测写入 PERFORMANCE_REPORT~~ → ✅ P95 121ms | 性能 9 |
| 2 | ~~**P2**~~ | ~~README sequenceDiagram + 截图~~ → README §问数全链路时序 | 文档 8→9 |
| 3 | ~~**P2**~~ | ~~CI compose smoke job~~ → 已用 `smoke-mock-demo.mjs` | CI 巩固 |
| 4 | ~~**P2**~~ | ~~应用层认证 / 限流 Roadmap 首期~~ → [SECURITY.md §应用层认证](../SECURITY.md#应用层认证与限流-roadmap首期--p2) | 安全 8→9 |
| 5 | ~~**P2**~~ | ~~ResultPanel 组件单测~~ → 已加 Vitest | 测试 8→9 |
| 6 | **P3** | ask 流式 UX | UX 8→9 |
| 7 | **P3** | 生产 HTTPS 部署实采 | 部署 8→9 |

---

## 与 ai-portfolio 矩阵对照

| 矩阵维度 | 矩阵 |
| --- | --- |
| README / Docker / CI / 压测 / 安全 / 部署 / 演示 | ✓ |
| 多租户 | N/A (by design) |
| Hub / 矩阵 | ✓ |

---

## 相关文档

- [PERFORMANCE_REPORT.md](../PERFORMANCE_REPORT.md)
- [docs/PERFORMANCE.md](./PERFORMANCE.md)
- [SECURITY.md](../SECURITY.md)
- [docs/USAGE.md](./USAGE.md)

*Round-6 均分 **8.4**（k6 复跑确认）· 下一目标：ask 流式 UX + 生产 HTTPS → **8.5+**。*
