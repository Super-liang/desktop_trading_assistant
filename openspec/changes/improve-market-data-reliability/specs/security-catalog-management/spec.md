## ADDED Requirements

### Requirement: PostgreSQL A 股证券目录
系统 SHALL 在 PostgreSQL 中保存 A 股证券的规范标识、交易所、代码、名称、数据来源和同步时间，并 SHALL 以该目录作为自选搜索与证券校验的事实来源。

#### Scenario: 按代码检索
- **WHEN** 已登录用户输入完整代码或代码前缀
- **THEN** 系统从 PostgreSQL 返回匹配的 A 股证券，结果包含规范证券标识、代码、交易所和名称

#### Scenario: 按名称检索
- **WHEN** 已登录用户输入股票名称片段
- **THEN** 系统从 PostgreSQL 返回名称匹配的 A 股证券且不调用实时行情接口

#### Scenario: 目录尚未准备完成
- **WHEN** 证券目录为空且首次同步尚未成功
- **THEN** 系统返回明确的目录准备中或暂不可用状态，不回退到 DEMO 或实时全市场搜索

### Requirement: 每日证券目录同步
系统 SHALL 在每天北京时间 08:00 从 AKShare 代码名称接口获取 A 股目录，完整校验后批量更新 PostgreSQL；多实例环境 SHALL 避免同一时刻重复执行同步。

#### Scenario: 每日同步成功
- **WHEN** 北京时间到达 08:00 且 AKShare 返回非空、格式有效的证券集合
- **THEN** 系统按规范证券标识 upsert 代码与名称并记录本次成功时间

#### Scenario: 同步失败或空响应
- **WHEN** AKShare 请求失败、超时、返回空集或包含无法完成整体校验的数据
- **THEN** 系统保留既有证券目录、记录失败且不清空已有数据

#### Scenario: 首次启动目录为空
- **WHEN** 服务启动后检测到证券目录为空
- **THEN** 系统异步触发一次目录同步且不阻塞 API 启动

#### Scenario: 多实例同时触发
- **WHEN** 多个 API 实例同时到达同步时间
- **THEN** 仅获得分布式锁的实例执行上游请求和数据库更新
