## ADDED Requirements

### Requirement: 未认证时隐藏透明浮窗
桌面应用 MUST 在没有有效用户会话时保持透明浮窗隐藏，并阻止所有原生入口显示空白浮窗。

#### Scenario: Windows 冷启动进入登录页
- **WHEN** 用户在没有有效会话的状态下启动 Windows 安装版
- **THEN** 系统仅展示主登录窗口且透明浮窗保持隐藏

#### Scenario: 未登录时使用托盘入口
- **WHEN** 未登录用户点击托盘中的“显示盯盘小窗”
- **THEN** 系统不得显示透明浮窗，并应保持或显示主登录窗口

#### Scenario: 退出登录
- **WHEN** 已登录用户退出当前会话
- **THEN** 系统立即隐藏透明浮窗并回到主登录窗口

### Requirement: 登录后允许用户控制透明浮窗
桌面应用 SHALL 在登录成功后允许用户通过现有入口展示或隐藏透明浮窗，但不得因登录成功自动强制弹出。

#### Scenario: 登录后手动展示
- **WHEN** 已登录用户点击主页或托盘的透明浮窗入口
- **THEN** 系统向浮窗同步当前会话并展示持仓行情内容

### Requirement: Windows 安装版连接生产 API
服务端 MUST 接受来源为 `http://tauri.localhost` 的受控跨域 API 请求，桌面生产构建 MUST 继续使用配置的 HTTPS API 地址。

#### Scenario: Windows 登录预检
- **WHEN** Windows Tauri WebView 以 `http://tauri.localhost` 为 Origin 对登录接口发起预检
- **THEN** 服务端返回成功响应并仅允许已配置的方法和请求头

#### Scenario: 未授权网页来源请求
- **WHEN** 不在允许列表中的网页 Origin 对 API 发起预检
- **THEN** 服务端拒绝该跨域请求

### Requirement: 窗口布局与可见性分离持久化
桌面应用 SHALL 继续保存窗口尺寸和位置，但 MUST NOT 使用历史可见性覆盖本次启动的认证窗口规则。

#### Scenario: 上次退出前浮窗可见
- **WHEN** 用户在浮窗可见时结束应用并在未登录状态重新启动
- **THEN** 系统恢复可用的尺寸和位置但不恢复浮窗可见性
