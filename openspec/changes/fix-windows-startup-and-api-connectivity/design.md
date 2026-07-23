## Context

Windows 安装版的 Tauri WebView 使用 `http://tauri.localhost` 作为生产页面源，而服务端 CORS 当前只允许本地开发源、`tauri://localhost` 和 `https://tauri.localhost`。线上预检请求已证明 `http://tauri.localhost` 被服务端以 `403 Invalid CORS request` 拒绝，因此浏览器层将登录表现为网络连接失败。

透明浮窗虽然在 `tauri.conf.json` 中配置了 `visible: false`，但窗口状态插件默认保存和恢复 `VISIBLE` 标志，历史状态可以覆盖静态配置。此外托盘菜单和老板键恢复逻辑没有认证状态保护，未登录时仍可能直接显示只渲染空内容的 ticker WebView。

## Goals / Non-Goals

**Goals:**

- Windows 安装版能够从 `http://tauri.localhost` 发起登录及后续 API 请求。
- 未认证状态下透明浮窗始终隐藏，且不能通过托盘或老板键恢复为空窗口。
- 保留透明浮窗的尺寸和位置记忆，不恢复其历史可见状态。
- 用服务端 CORS 测试和桌面端状态逻辑测试覆盖根因。

**Non-Goals:**

- 不改变认证协议、令牌格式或用户数据结构。
- 不改变线上 API 地址、TLS 配置和行情数据逻辑。
- 本变更不发布新版本安装包，也不直接部署线上服务；完成本地验证后再由用户确认发布。

## Decisions

### 1. 明确允许 Windows Tauri 的生产 Origin

在现有精确 CORS 允许列表中加入 `http://tauri.localhost`，继续限制为应用所需的固定 Origin。相比允许任意来源或宽泛通配符，该方案保持最小授权，并与 Tauri Windows 的实际生产协议一致。

### 2. 原生层维护认证可见性状态

新增仅存在于当前进程内的认证状态，由主窗口和 ticker 窗口在会话变化时通过 Tauri command 同步。认证变为 false 时原生层立即隐藏 ticker；托盘菜单和老板键恢复 ticker 前也检查该状态。选择原生层作为最终门禁，是因为单纯返回空 React 内容无法阻止操作系统窗口本身显示。

### 3. 窗口状态插件不持久化可见性

窗口状态插件只保留尺寸、位置、最大化、装饰和全屏等布局信息，排除 `VISIBLE`。应用每次启动均以配置文件中的 `main: visible`、`ticker: hidden` 为准，避免上次退出时的窗口状态绕过认证。

### 4. 保持登录成功后的现有展示方式

登录成功后只同步“允许展示 ticker”的认证状态，不强制弹出透明浮窗；用户仍通过主页按钮、托盘菜单或老板键控制展示。这样既满足“登录后再展示”，也避免登录瞬间打扰用户。

## Risks / Trade-offs

- [多个 WebView 会短暂重复同步认证状态] → 状态更新设计为幂等；false 始终隐藏 ticker，true 只解锁而不主动展示。
- [只修代码但未部署时旧安装版仍无法登录] → 本地验证报告明确区分代码修复、服务端部署和 Windows 新包发布三个阶段。
- [窗口可见性不再跨重启恢复] → 这是认证安全所需；尺寸和位置仍继续保存。

## Migration Plan

1. 合并并部署服务端 CORS 修复，使现有 Windows 安装包的登录请求可通过预检。
2. 构建包含窗口生命周期修复的新 Windows 安装包。
3. 在 Windows 上验证冷启动、登录、托盘、老板键和退出登录场景。
4. 若出现回归，可回滚应用安装包；CORS 新增固定可信 Origin 可独立保留或随服务端版本回滚。

## Open Questions

无。根因已由 Tauri 本地依赖源码和线上 CORS 预检结果确认。
