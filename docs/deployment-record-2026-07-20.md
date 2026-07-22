# 云端部署记录（2026-07-20）

## 最终状态

- 公网入口：`https://211.159.158.165`
- 应用版本：`/opt/stock-watch/releases/cloud-20260720-3`
- 架构：宝塔 Nginx → Spring API `127.0.0.1:8080` → AKShare `127.0.0.1:8090`
- PostgreSQL：18.0，`127.0.0.1:5433`，数据库 `trading`
- 服务：`stock-watch-api.service`、`stock-watch-akshare.service` 均为 active/enabled
- 证书续期：`stock-watch-cert-renew.timer` 为 active/enabled，续期 dry-run 成功
- 数据库备份：`stock-watch-db-backup.timer` 为 active/enabled，每天约 03:20 执行

首次管理员账号和随机密码保存在服务器 `/etc/stock-watch/bootstrap-credentials`，文件权限为 `0600`。API 环境文件中的一次性管理员明文已经清空；重启后使用数据库中的 BCrypt 密码仍可正常登录。

## 验证证据

- 本地 Java：14 项测试通过，包含生产嵌套行情配置绑定回归测试。
- 本地 Python：21 项测试通过。
- 本地桌面：18 项测试通过，Web 云端构建成功。
- 部署 Shell 静态门禁、备份损坏/恢复工具失败/systemd 启动失败测试、`git diff --check` 与 OpenSpec strict 校验通过。
- 发布包 `cloud-20260720-3` SHA-256：
  `9a38f6837606f3da214581d127f3c927dc8d949353a05c1393e8604e4807da75`
- 发布脚本在 root 解包前强制验证可移植 SHA-256；AKShare 使用随 release 原子切换的 root-owned venv。
- Nginx 实机 `nginx -t` 通过，HTTP 返回 308；HTTPS 首页标题为“股票盯盘助手”，并包含 HSTS、CSP、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy` 与 `Permissions-Policy`，未认证 API 返回 401。
- PostgreSQL、API、AKShare 仅监听回环地址；公网地址对 5433、8080、8090 的协议连接超时。
- 注册、登录、管理员用户列表、账号注销通过。
- AKShare 实测 `SSE:600519` 返回 `source=AKSHARE`、`demo=false`。
- 持仓实测 100 股、成本 1000 元时，行情 1327.5 元，市值 132750 元、浮盈 32750 元；测试持仓和测试账号随后已删除。
- Let’s Encrypt IP 证书签发成功，有效期为 2026-07-20 至 2026-07-27，模拟续期成功。
- 首次迁移前备份：
  `/var/backups/stock-watch/trading-20260720T113141Z.dump`
- 自动备份首次演练：
  `/var/backups/stock-watch/trading-20260720T114641Z.dump`
- 两份备份均通过 SHA-256 与 `pg_restore --list` 校验。
- 最新备份 `/var/backups/stock-watch/trading-20260720T115732Z.dump` 已完整恢复到受保护的临时数据库；验证得到 5 张业务表与 1 条成功 Flyway 记录，随后删除临时库并再次确认其不存在。
- 服务器 `verify.sh` 完整复验通过；该脚本会自动创建并清理验收账号与持仓，同时检查证书、定时器、数据库、备份和日志脱敏。

## 已知事项

- AKShare 官方公开数据只适合非商业研究验证，ToC 商业上线需要替换为有展示和分发授权的行情源。
- Flyway 当前记录 PostgreSQL 18 高于其已验证支持版本 17；本次迁移成功，但后续应升级 Flyway 后再新增迁移。
- SELinux 在部署前已处于 Disabled，firewalld 在部署前未运行。本次通过回环监听和 PostgreSQL访问控制收口内部端口；建议后续安排独立安全加固窗口。
- 自动备份暂不删除历史文件，需监控 `/var/backups/stock-watch` 磁盘占用并另行确认保留周期。

## 回滚入口

- 停止应用但保留数据库：`systemctl stop stock-watch-api stock-watch-akshare`
- 当前数据库配置前置备份：
  `/www/server/pgsql/data/pre-stock-watch-20260720T113141Z`
- 当前 HTTPS 站点切换前备份：
  `/www/server/panel/vhost/nginx/stock-watch.conf.pre-https.*`
- 数据库恢复会覆盖现有数据，必须在维护窗口重新确认影响范围后，使用对应 custom-format 备份执行 `pg_restore`；部署脚本不会自动执行破坏性恢复。
