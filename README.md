# 股票盯盘助手

面向个人用户的 Windows/macOS 极简盯盘应用。当前一期实现注册登录、用户后台、A 股自选与手工持仓、实时盈亏、透明置顶小窗、托盘和全局老板键；真实行情通过可配置的 AKShare 研究网关接入，生产配置禁止启用 `DEMO` Provider。

> 当前演示行情不是真实交易所行情，不构成任何投资建议。正式商用前必须取得沪深行情 PC 展示及终端分发授权。

## 已实现

- Tauri 2 原生壳：Windows/macOS、透明无边框置顶小窗、托盘、`Cmd/Ctrl + Shift + H` 老板键。
- React 工作台：注册登录、自选搜索、持仓数量/成本、实时市值/浮盈亏、管理员用户启停。
- Spring Boot API：Java 17、JWT、刷新令牌轮换、BCrypt、RBAC、账号注销、审计。
- 行情网关：`QuoteProvider` SPI、Provider 健康/能力、A 股代码规范化、Redis 全市场快照、单股查询、SSE 与 AKShare HTTP 网关。
- PostgreSQL + Flyway、Docker Compose、后端/前端测试与 Win/macOS CI 编译配置。

## 目录

```text
apps/desktop     React + TypeScript + Tauri 2
services/api     Java 17 + Spring Boot 3.5
services/akshare-gateway  Python + FastAPI + AKShare（本地非商业研究）
openspec         中文 proposal / design / specs / tasks
docs             架构、竞品与合规说明
compose.yaml     PostgreSQL + API
```

## 本地运行

前置：JDK 17、Node.js 22+、Rust stable。Docker Desktop 用于 PostgreSQL/生产近似环境，但不是本地演示的硬依赖。

```bash
cp .env.example .env
```

请先修改 `.env` 中的数据库密码、`JWT_SECRET`（至少 32 字节）和初始管理员密码。

无 Docker 的本地演示可使用内置 H2：

```bash
cd services/api
SPRING_PROFILES_ACTIVE=local JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw spring-boot:run
```

使用 PostgreSQL 启动完整服务端：

```bash
docker compose up --build
```

启动桌面 Web 开发界面：

```bash
npm install
npm run dev:desktop
```

启动原生桌面应用：

```bash
npm run tauri:dev
```

API 健康检查：`http://localhost:8080/actuator/health`。

## OpenCloudOS 云服务器部署

OpenCloudOS 9.6 单机部署使用现有宝塔 Nginx + systemd，复用现有宿主机 PostgreSQL，并支持无域名的 Let’s Encrypt 短期 IP 证书。完整预检、影响范围、备份、发布、回滚和验收步骤见 [OpenCloudOS 云端部署手册](docs/cloud-deployment-opencloudos.md)。

服务器托管的是 Web 前端、Spring API 和 AKShare 网关；Tauri 透明小窗、老板键和托盘仍在用户本机运行。

## macOS 无 Docker 接入 AKShare 真实公开行情

> AKShare 官方声明其接口和数据仅用于学术研究、不可商业使用。以下方式只用于本地开发和非商业功能验证，不能作为 ToC 正式发行的数据源，也不代表交易所授权实时行情。

首次安装 Python 依赖（Python 3.9+，建议使用项目隔离虚拟环境）：

```bash
cd services/akshare-gateway
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
```

终端一：生成一个本机共享密钥并启动 AKShare 网关：

```bash
cd services/akshare-gateway
export AKSHARE_API_KEY="$(openssl rand -hex 24)"
echo "请将本终端的 AKSHARE_API_KEY 值复制到终端二"
.venv/bin/python -m uvicorn akshare_gateway.app:create_app \
  --factory --host 127.0.0.1 --port 8090
```

健康检查不会触发上游抓取，也不会暴露密钥：

```bash
curl http://127.0.0.1:8090/health
```

终端二：填入与终端一完全相同的密钥，启用优先级高于 DEMO 的 HTTP Provider：

```bash
cd services/api
export QUOTE_HTTP_ENABLED=true
export QUOTE_HTTP_BASE_URL=http://127.0.0.1:8090
export QUOTE_HTTP_API_KEY='替换为终端一生成的 AKSHARE_API_KEY'
export QUOTE_HTTP_PRIORITY=10
SPRING_PROFILES_ACTIVE=local JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  ./mvnw spring-boot:run
```

终端三：启动桌面应用：

```bash
npm run tauri:dev
```

添加 `600519`、`000001` 等自选后，每条行情应显示 `AKSHARE`、抓取时间和“延迟估算”；主界面与透明小窗也会显示 `AKSHARE` 提示。验证 Provider 状态可在登录后请求 `GET /api/v1/quotes/providers`。停止网关或上游异常时，短时间内保留并标记陈旧快照，超过默认 30 秒后 Spring Registry 会降级到明确标识的 `DEMO`。全市场快照默认缓存 10 秒，可通过 `AKSHARE_CACHE_TTL_SECONDS` 调整；不建议缩短到桌面 2 秒刷新频率，以免触发公开源限流。

回滚到纯演示行情只需停止网关，以 `QUOTE_HTTP_ENABLED=false` 重启 API；无需修改数据库。

## 测试与构建

```bash
cd services/api
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw test

cd ../akshare-gateway
.venv/bin/python -m pytest

cd ../..
npm run test:desktop
npm run build:desktop
cargo check --manifest-path apps/desktop/src-tauri/Cargo.toml
```

Windows 安装包必须在 Windows runner 构建和签名；macOS 安装包必须配置 Apple Developer 证书、签名并公证。仓库 CI 会做双平台编译，但签名凭据需由发行主体提供。

## 生产行情接入

新增实现 `QuoteProvider` 的适配器并配置更高优先级即可接入新源。上线前必须验证供应商合同明确覆盖：

- 沪、深（如需北交所则另含北交所）；
- PC/桌面软件展示；
- 中国内地目标地域；
- 向本应用终端用户展示/分发；
- 服务端缓存、流式扇出和盈亏等衍生计算。

禁止把同花顺、东方财富、雪球等网页或消费产品私有接口作为生产源。

仓库中的 AKShare 网关是开发/研究适配器，不是生产授权 Provider。网关优先使用 `stock_zh_a_spot_em()`，失败时切换至 `stock_zh_a_spot()`；两者均返回公开网站的沪深京全市场快照。网关默认缓存 10 秒并在无法证明交易所源时间的情况下标记 `delayed=true`；ToC 发布前必须替换为合同明确授权的供应商。

## 当前边界

- 一期不接交易、不采集券商账号/交易密码、不含费用税费/分红送转计算。
- 当前会话仅保存在进程内存并通过 Tauri 事件同步到小窗，不在磁盘明文保存；若后续要支持重启免登录，必须先接入 OS Keychain/Stronghold。
- 获授权行情可通过 `QUOTE_HTTP_*` 配置接入标准桥接器，并在调用失败时自动降级至下一 Provider；接口契约见 [docs/architecture.md](docs/architecture.md)。
- Windows 老板键、透明窗、代码签名，以及 macOS Intel/Apple Silicon、公证需要发行前真机复验。
- 二期个性化/会员与三期解释型 AI 的规划见 [产品与合规说明](docs/product-and-compliance.md)。
