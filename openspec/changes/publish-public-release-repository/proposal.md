## Why

当前源码仓库为私有仓库，外部用户无法查看产品介绍或下载已构建安装包。需要建立一个独立公开仓库作为纯分发入口，同时严格避免泄露源码、部署配置、服务器信息、用户数据和开发凭证。

## What Changes

- 创建公开仓库 `Super-liang/stock-trading-assistant-releases`。
- 公开仓库 Git 历史只提交一份面向最终用户的 `README.md` 产品介绍与下载说明。
- 创建 `v0.1.3` GitHub Release，上传 macOS arm64 DMG、APP ZIP、Windows x64 NSIS 安装器及两个平台的 SHA-256 清单。
- README 中所有下载链接指向新公开仓库，不引用私有源码仓库的文档、工作流或克隆地址。
- 发布前后执行文件清单、敏感信息、仓库可见性、Release 资产和 SHA-256 校验。

## Capabilities

### New Capabilities

- `public-binary-distribution`: 约束公开仓库只分发产品介绍与经过校验的安装包，不暴露私有实现或运维信息。

### Modified Capabilities

无。桌面应用和云端服务行为不变。

## Impact

- GitHub 新增公开仓库和公开 Release。
- 当前私有源码仓库新增本变更的 OpenSpec 文档，不复制业务源码到公开仓库。
- 不修改生产服务器、数据库、API 或既有私有 Release。
