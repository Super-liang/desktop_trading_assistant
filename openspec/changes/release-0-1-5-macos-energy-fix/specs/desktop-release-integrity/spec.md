## ADDED Requirements

### Requirement: 桌面版本元数据一致
桌面应用发布构建 MUST 在前端包、Tauri 配置、Rust 包和最终应用元数据中使用同一版本号。

#### Scenario: 构建 0.1.5 macOS 应用
- **WHEN** 执行 0.1.5 生产构建
- **THEN** npm、Cargo、Tauri 与 `Info.plist` 均显示 0.1.5

### Requirement: 覆盖安装对应构建产物
本机覆盖安装 MUST 在旧进程退出后使用已验证构建产物，并保留可恢复的旧应用副本。

#### Scenario: 覆盖旧版应用
- **WHEN** `/Applications` 中存在修复前的同名应用
- **THEN** 系统先退出旧进程、备份旧应用，再完整安装新构建

### Requirement: 验证实际运行版本
安装验收 MUST 核对最终运行路径、版本元数据和二进制指纹，并验证关键交互与空闲能耗。

#### Scenario: 0.1.5 安装后验收
- **WHEN** 从 `/Applications` 启动股票盯盘助手 0.1.5
- **THEN** 登录、导航和透明小窗可响应，且应用与 WebKit 子进程不再保持持续高 CPU
