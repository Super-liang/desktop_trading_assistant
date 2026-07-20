## ADDED Requirements

### Requirement: 管理员 RBAC
管理接口 SHALL 仅允许具有 ADMIN 角色的已认证用户访问；普通用户访问时 SHALL 返回 403。

#### Scenario: 普通用户访问后台
- **WHEN** 普通用户请求用户管理接口
- **THEN** 系统返回 403 且不返回任何用户数据

### Requirement: 用户管理
管理员 SHALL 能分页搜索用户、查看账号状态/角色/注册时间/最近登录时间，并能启用或禁用账号；接口不得返回密码摘要、令牌或持仓明细。

#### Scenario: 禁用用户
- **WHEN** 管理员禁用一个普通用户
- **THEN** 系统更新账号状态、撤销其刷新会话并记录审计事件

### Requirement: 管理审计
系统 SHALL 记录管理员、动作、目标、时间和结果，审计记录 SHALL 不包含密码、Token 或持仓成本。

#### Scenario: 查询审计记录
- **WHEN** 管理员分页查询审计日志
- **THEN** 系统按时间倒序返回脱敏的管理操作记录

