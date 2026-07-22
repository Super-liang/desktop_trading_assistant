## Context

根 README 是 GitHub 访问者的第一入口，但现有内容形成于早期功能阶段，混合了演示模式、本地开发、云部署和生产合规说明，快速开始路径较长，并包含与 0.1.3 当前实现不一致的缓存、降级和发布状态描述。项目现已提供 GitHub 双平台安装包、Apple Container 本地基础设施、真实 AKShare 网关、双行情模式和完整 OpenCloudOS 部署脚本，需要重新组织信息层级。

## Goals / Non-Goals

**Goals:**

- 让普通用户在首屏识别产品定位、核心能力并直接进入 Release 下载。
- 让开发者按 macOS Apple Container 或通用本地环境快速启动完整链路。
- 让维护者快速定位架构、测试、构建、云部署和回滚文档。
- 确保功能、端口、脚本、版本、权限和合规边界与仓库当前事实一致。
- 使用徽章、表格、折叠说明、Mermaid 架构图和适度图标提高可读性。

**Non-Goals:**

- 不修改业务代码、配置默认值或部署拓扑。
- 不将 README 扩展为完整运维手册，复杂操作继续链接到 `docs/`。
- 不夸大 AKShare 数据的实时性、稳定性或商用授权。
- 不声称 macOS 安装包已经 Apple notarization。

## Decisions

### 1. 采用“三类读者、两条快速路径”结构

首屏服务普通用户，先给产品价值、Release 下载和核心功能；“快速开始”区分直接安装与源码开发；架构、部署和扩展内容后置。相比从依赖安装开始的传统 README，这种结构能减少非开发用户的认知负担。

### 2. macOS 本地开发以 Apple Container 为主路径

用户当前明确不使用 Docker，仓库开发环境也已通过 Apple Container 验证 PostgreSQL 与 Redis，因此 README 给出 Apple Container 命令作为 macOS 推荐路径，同时保留 Docker Compose 作为通用后端基础设施路径。应用服务继续在可见终端中分别启动，便于开发者随时停止。

### 3. 命令只引用已存在的脚本和真实端口

快速启动使用 `npm`、Maven wrapper、Python venv、`container`、`curl` 等仓库当前工具；端口固定说明为桌面 Vite 1420、Spring 8080、AKShare 8090、PostgreSQL 5432 和 Redis 6379。敏感值只使用环境变量占位，不在文档中提供真实凭证。

### 4. 用 Mermaid 描述请求链路而不是堆叠技术名词

README 使用一张小型架构图表达桌面端、Spring、PostgreSQL、Redis、AKShare 与公开上游之间的关系；详细设计仍链接 `docs/architecture.md`。徽章只使用 GitHub、Tauri、React、Spring、AKShare、许可证和版本等稳定信息，避免依赖未知服务。

### 5. 显式区分“已实现、规划中、已知边界”

当前 0.1.3 功能使用完成标记；二期个性化/会员和三期 AI 诊股仅放路线图，不写成已上线。行情合规、东财公开源波动、macOS 未公证、非交易时段保留快照等限制放在独立提示中。

## Risks / Trade-offs

- [README 命令随实现演进而过时] → 使用仓库脚本、端口表和链接，避免复制大段底层配置；发布时把 README 检查纳入任务。
- [装饰过多降低专业性] → 图标只用于导航和章节识别，正文保持简洁，不使用大段居中 HTML 或动态图片。
- [Apple Container 命令因已有同名容器失败] → 快速开始提供首次创建和后续启动两组命令，并说明容器名。
- [公开行情被误解为商用授权实时行情] → 首屏和合规章节均明确 AKShare 仅用于研究验证，生产 ToC 必须替换授权源。
- [macOS 安装包触发 Gatekeeper] → 下载说明明确当前为 ad-hoc 签名、未公证，并提供系统允许范围内的首次打开提示。
