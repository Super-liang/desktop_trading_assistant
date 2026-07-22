## 1. 发布构建基础

- [x] 1.1 增加公共发布配置、版本化命名和云 API 静态资源校验脚本
- [x] 1.2 增加发布脚本的定向自动化测试

## 2. macOS 安装包

- [x] 2.1 实现 Apple Silicon DMG 与 APP ZIP 构建、归档和 SHA-256 生成脚本
- [x] 2.2 在本机执行 macOS 构建并验证镜像、签名结构、架构和云 API

## 3. Windows 安装包

- [x] 3.1 实现 Mac 上基于 cargo-xwin 的 Windows x64 NSIS 交叉构建脚本
- [x] 3.2 增加 Windows 原生 runner 的 NSIS、MSI 构建与 artifacts 上传 workflow
- [x] 3.3 执行可用的 Windows 构建路径并验证 PE 文件、命名和 SHA-256 清单

## 4. 交付与质量门禁

- [x] 4.1 编写安装、系统安全提示、哈希校验和正式签名升级说明
- [x] 4.2 执行 OpenSpec 严格校验、项目测试和安装包验证
- [x] 4.3 分别完成 verification 与 code-review 检查并记录未能执行的平台真机验收项
