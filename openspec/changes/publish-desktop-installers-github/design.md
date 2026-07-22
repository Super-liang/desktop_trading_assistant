## Context

仓库已有 `Desktop installers` workflow，可在 GitHub 托管的 macOS 与 Windows Runner 上分别构建安装包，并上传为 Actions Artifact。当前流程仅支持 `workflow_dispatch`，且 `contents: read`，因此产物有保留期、没有稳定下载链接，也无法形成面向 ToC 用户的正式版本。

本次发布仍使用应用真实版本 `0.1.0`，安装包内嵌生产 API `https://211.159.158.165`。Windows 必须由 Windows Runner 生成，macOS ARM64 必须由 macOS Runner 生成。

## Goals / Non-Goals

**Goals:**

- 使用 `v<应用版本>` 标签触发同一套双平台构建。
- 复用现有平台构建与校验脚本，不改变安装包内容约定。
- 仅在所有平台成功后创建 GitHub Release 并上传全部文件。
- 使用最小化的 GitHub Actions 权限完成发布。

**Non-Goals:**

- 不增加自动更新器、代码签名证书或 Apple 公证。
- 不生成 Intel macOS 安装包或 Windows ARM 安装包。
- 不修改业务功能、后端部署或用户数据。

## Decisions

1. **标签与手动触发共用一个 workflow。** `push.tags: ['v*']` 负责正式发布，`workflow_dispatch` 继续用于预构建。相比新建第二份 workflow，可避免两套构建命令漂移。
2. **生产 API 地址使用表达式默认值。** 手动触发采用用户输入，标签触发没有 inputs 时回退到 `https://211.159.158.165`，确保构建资源不会回退到 localhost。
3. **发布任务依赖两个构建任务。** `release` job 使用 `needs` 汇合平台结果，下载两个 Artifact 后创建 Release。任一构建或校验失败，发布任务不会运行。
4. **使用 GitHub CLI 与 Actions 临时令牌。** 发布 job 单独授予 `contents: write`，通过 Runner 自带 `gh` 创建草稿 Release、幂等覆盖上传资产、核对远端资产集合后再转为正式 Release；构建 job 保持只读。相比个人访问令牌，这一方案没有长期密钥管理负担，并避免上传中断留下公开半成品。
5. **标签版本必须与应用版本一致。** 发布任务在创建 Release 前比较 `GITHUB_REF_NAME` 与 `apps/desktop/package.json`，避免标签、文件名和应用元数据不一致。
6. **资产收集不依赖 Artifact 展开层级。** 独立脚本在各平台 Artifact 根目录内按精确文件名递归查找，并要求每种资产唯一匹配，以兼容 `upload-artifact` 保留通配符之后目录层级的行为。

## Risks / Trade-offs

- [macOS 使用 ad-hoc 签名，未做 Apple 公证，首次打开可能触发系统安全提示] → Release 说明明确该限制，后续获取 Developer ID 后再补正式签名与公证。
- [GitHub 托管 Runner 或依赖源临时故障导致构建失败] → Release job 受 `needs` 保护，不产生半成品；可重跑失败任务。
- [重复推送已存在标签] → 禁止覆盖标签；需要修复时提升应用版本并创建新标签。
- [同名 SHA256SUMS 文件冲突] → 资产收集脚本按平台根目录查找并复制为带平台后缀的唯一文件名，上传前验证总数严格等于 6。
- [Release 上传中断或任务重跑] → 未完成版本始终保持 draft；重跑对 draft 使用 `--clobber`，远端六项资产严格一致后才解除 draft。已正式发布且资产一致时直接成功退出。

## Migration Plan

1. 更新并验证 workflow 语法与发布守卫测试。
2. 将发布流程提交并合并到 `main`。
3. 在 `main` 当前提交创建不可变标签 `v0.1.0` 并推送。
4. 等待两个平台构建及发布任务成功，核验 Release 资产和校验和。
5. 如构建失败，不创建 Release；修复后删除未发布的远端标签需用户明确批准，默认改用新补丁版本。

## Open Questions

无。
