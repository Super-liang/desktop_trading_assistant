## Context

修复位于当前 `codex/akshare-market-source` 分支，分支基线与远端 `main` 一致。线上采用不可变 release 目录、`current` 原子软链接和 systemd 服务；发布前必须生成并校验 PostgreSQL 备份。桌面安装包由私有仓库的标签触发 GitHub Actions，在 macOS 与 Windows 原生 runner 上分别构建，公开仓库只允许提交 README，二进制只能作为 Release 资产上传。

## Goals / Non-Goals

**Goals:**

- 以可回滚方式部署服务端 CORS 修复，并验证 Windows Origin 的公网预检。
- 发布版本号一致的 macOS ARM64 和 Windows x64 0.1.4 安装资产。
- 在公开下载仓库发布经过校验的 0.1.4 资产和 README。
- 保持私有源码、服务器配置和凭据不进入公开仓库。

**Non-Goals:**

- 不修改数据库结构或执行数据恢复。
- 不对 0.1.4 增加 Windows ARM64、macOS Intel 或自动更新能力。
- 不删除或覆盖已有 v0.1.3 Release。

## Decisions

### 1. 先部署服务端，再发布桌面安装包

Windows 0.1.3 的登录失败由服务端 CORS 引起。先部署并以公网预检验证，可以让旧版立即恢复登录，也可避免 0.1.4 发布后仍连接旧服务。部署前使用现有备份脚本生成有效的 PostgreSQL 备份标识，再调用原子发布脚本。

### 2. 0.1.4 统一升级三个版本来源

同步修改 npm workspace、Tauri 配置和 Cargo 包版本，并让 lockfile 由对应包管理工具机械更新。构建前运行版本一致性与安装包门禁测试，避免安装程序、可执行文件和 Release 标签不一致。

### 3. 通过标签使用原生 CI 构建

在修复提交进入 `main` 后创建并推送 `v0.1.4` 标签，复用现有 GitHub Actions 的 macOS ARM64 与 Windows x64 runner。相比在 macOS 上交叉构建，Windows 原生 runner 更适合验证 WebView2、PE 架构和 NSIS 输出。

### 4. 私有 Release 验证后再同步公开 Release

等待私有仓库工作流完成，下载全部资产并验证名称、数量、架构、嵌入 API 地址和 SHA-256。公开仓库使用独立克隆，只修改 README，并创建同名 `v0.1.4` Release；之后用无凭据直链重新下载并复验。

## Risks / Trade-offs

- [云端发布重启 API 造成短暂切换] → 使用现有原子发布和健康检查，失败自动回滚到上一 release。
- [GitHub Actions 任一平台失败] → 不创建公开 Release，保留日志并修复后重跑。
- [公开仓库误上传源码或配置] → Git 跟踪文件白名单限定为 README，Release 资产限定为预期安装文件和校验文件。
- [Windows 行为无法在当前 Mac 上完整实测] → 以原生 Windows CI 构建门禁为基础，并在发布后明确要求 Windows 实机最终验收。

## Migration Plan

1. 更新版本号并完成本地自动化验证。
2. 仅暂存本次修复、测试、版本文件和相关 OpenSpec，提交后推送当前分支。
3. 将修复分支快进合并到 `main` 并推送。
4. 构建云端 release 包，上传服务器，生成数据库备份并原子部署。
5. 验证线上健康、CORS、认证和服务状态。
6. 创建 `v0.1.4` 标签，等待私有安装包工作流完成。
7. 校验并同步资产至公开仓库，更新 README。

回滚时将服务器 `current` 链接切回前一 release 并重启服务；GitHub Release 保留但可标记为预发布或删除失败资产。数据库无迁移，通常无需恢复备份。

## Open Questions

无。继续沿用现有腾讯云主机、API 地址和双仓库发布模型。
