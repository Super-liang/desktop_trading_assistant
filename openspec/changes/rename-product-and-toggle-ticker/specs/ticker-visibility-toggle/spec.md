## ADDED Requirements

### Requirement: 透明小窗入口切换真实可见状态
主界面“透明小窗”按钮 SHALL 每次点击查询 ticker 原生窗口当前可见状态，并 SHALL 在显示与隐藏之间切换。

#### Scenario: 隐藏已显示的小窗
- **WHEN** ticker 当前可见且用户点击“透明小窗”
- **THEN** 系统隐藏 ticker，且不重复执行显示

#### Scenario: 显示已隐藏的小窗
- **WHEN** ticker 当前隐藏且用户点击“透明小窗”
- **THEN** 系统同步当前会话、显示 ticker 并使其获得焦点

### Requirement: 外部可见状态变化不导致失真
按钮行为 SHALL 以原生窗口实时状态为事实来源，MUST NOT 依赖可能与老板键、托盘或小窗自身隐藏操作失步的本地缓存。

#### Scenario: 小窗自行隐藏后重新显示
- **WHEN** ticker 通过自身“隐藏”按钮变为隐藏且用户随后点击主界面“透明小窗”
- **THEN** 系统重新查询到隐藏状态并显示 ticker

#### Scenario: 浏览器开发模式
- **WHEN** 页面运行在没有 Tauri 原生窗口的浏览器环境
- **THEN** 点击入口不会使主界面崩溃
