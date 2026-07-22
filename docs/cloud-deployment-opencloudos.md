# OpenCloudOS 9.6 单机云端部署

## 部署边界

服务器运行 React Web、Spring API、AKShare 研究网关和仅回环访问的 Redis 行情缓存，并复用现有宝塔 Nginx 与宿主机 PostgreSQL 18。Tauri 桌面壳仍安装在 Windows/macOS 本机；Web 版不具备透明小窗、老板键和托盘。

当前目标：

- OpenCloudOS 9.6 x86_64
- 公网 IP：`211.159.158.165`
- PostgreSQL：`/www/server/pgsql`，端口 5433、数据库 `trading`、用户 `trading`
- Redis：宿主机 `127.0.0.1:6379`，启用密码，只保存可重建的全市场行情快照
- 暂无域名，使用 Let’s Encrypt 六天期 IP 地址证书

AKShare 只用于本地/云端非商业研究验证，不能作为 ToC 商业行情授权。

## 首次 SSH 授权

在 Mac 终端执行并输入服务器密码：

```bash
ssh-copy-id -i ~/.ssh/id_rsa.pub -p 22 root@211.159.158.165
ssh -p 22 root@211.159.158.165
```

不要把 root 密码、数据库密码或私钥发送到聊天、写进仓库或保存在 shell history。

## 发布前只读预检

上传 `deploy/opencloudos` 后，以 root 执行：

```bash
bash deploy/opencloudos/scripts/preflight.sh
```

必须确认：

- PostgreSQL 18.0 当前 `listen_addresses='*'`，上线前必须改为 `127.0.0.1`；
- `pg_hba.conf` 当前存在 `host trading trading 0.0.0.0/0 md5`，上线前必须删除或收窄；
- 云安全组只开放 22/80/443，5433 和 6379 不对公网；
- SELinux 在本次部署前已经处于 Disabled；本次不自动开启，后续应安排独立加固窗口；
- firewalld 在本次部署前未运行；本次不自动启用，避免影响宝塔、SSH 和 Docker；
- 根分区和 `/opt` 有足够空间；
- 当前 PostgreSQL 备份可恢复。

## 本地构建发布包

```bash
bash deploy/tests/test-assets.sh
bash deploy/build-release.sh
```

Web 构建使用同源 `/api`；JAR 使用 `prod` profile；AKShare Python 源码和锁定依赖一起进入发布包。输出位于 `build/releases/`。

## 主机安装影响范围

`install-host.sh` 会执行以下写操作，必须在预检后确认：

- 通过 dnf 安装 Java 17、Python、Redis 与基础命令，不安装第二套 Nginx 或 PostgreSQL；
- 创建无登录用户 `stockwatch`；
- 创建 `/opt/stock-watch`、`/etc/stock-watch`、`/var/backups/stock-watch`；
- 安装 systemd 配置，并向宝塔 vhost 目录新增 `/www/server/panel/vhost/nginx/stock-watch.conf`；
- 若 SELinux 已启用，才设置 Nginx 回环代理所需的最小策略；
- 若 firewalld 未运行，仅输出告警，不擅自启用。

它不会创建、删除、重启或升级 PostgreSQL，也不会覆盖宝塔 Nginx 主配置。数据库公网暴露必须通过 PostgreSQL 自身配置和腾讯云安全组另行收口。

```bash
bash deploy/opencloudos/scripts/install-host.sh
```

根据 `.example` 创建环境文件，密码只在服务器本地编辑：

```bash
install -m 600 /etc/stock-watch/api.env.example /etc/stock-watch/api.env
install -m 600 /etc/stock-watch/akshare.env.example /etc/stock-watch/akshare.env
vi /etc/stock-watch/api.env
vi /etc/stock-watch/akshare.env
```

安装脚本不会在设置密码前自动启动 Redis。编辑 `/etc/redis/redis.conf`（部分发行包路径为 `/etc/redis.conf`），至少确认：

```text
bind 127.0.0.1 ::1
protected-mode yes
port 6379
requirepass <独立强密码>
```

将同一密码写入 root-only 的 `/etc/stock-watch/api.env`：

```text
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=<独立强密码>
QUOTE_DEMO_ENABLED=false
```

然后执行并验证 Redis 不监听公网：

```bash
systemctl enable --now redis
REDISCLI_AUTH='<独立强密码>' redis-cli -h 127.0.0.1 ping
ss -lnt | grep 6379
```

腾讯云安全组不需要新增端口，严禁开放 6379。

首次管理员创建成功后，应清空 `ADMIN_EMAIL` 和 `ADMIN_PASSWORD` 并重启 API。

## PostgreSQL 备份

在服务器当前 shell 临时加载 root-only 环境文件：

```bash
set -a
source /etc/stock-watch/api.env
set +a
export PG_BIN=/www/server/pgsql/bin
export PG_HOST=127.0.0.1
export PG_PORT=5433
export DB_NAME=trading
bash deploy/opencloudos/scripts/backup-postgres.sh
```

脚本使用 PostgreSQL 18 自带客户端执行 `pg_dump -Fc`，验证文件非空并用 `pg_restore --list` 解析，输出形如 `20260720T120000Z` 的 `BACKUP_ID`。发布脚本没有该标识会拒绝运行。

上线后启用 `stock-watch-db-backup.timer`，每天约 03:20（带最多 20 分钟随机延迟）执行同一套备份与校验，文件保存在 `/var/backups/stock-watch/`。当前不自动删除历史备份；应监控磁盘并在确认保留周期后再增加清理策略。

```bash
systemctl list-timers stock-watch-db-backup.timer
systemctl status stock-watch-db-backup.service
journalctl -u stock-watch-db-backup.service --since today
```

备份完成并验证后，在维护窗口收口数据库网络：

1. 备份 `/www/server/pgsql/data/postgresql.conf` 与 `pg_hba.conf`。
2. 将 `listen_addresses` 改为 `127.0.0.1`。
3. 删除或收窄 `host trading trading 0.0.0.0/0 md5`。
4. 受控重启 PostgreSQL，并验证 `127.0.0.1:5433` 可用。
5. 在腾讯云安全组删除 5433 入站规则，再从公网确认端口不可达。

以上步骤会短暂中断数据库连接，必须在用户明确批准后执行。

迁移前或定期恢复演练需要创建一个带时间戳的临时数据库，完整执行 `pg_restore`，验证用户表与 Flyway 历史后再删除该临时数据库。因为删除数据库属于破坏性操作，脚本要求调用者把精确数据库名重复传入 `CONFIRM_DROP_DATABASE`：

```bash
export BACKUP_FILE=/var/backups/stock-watch/trading-<backup-id>.dump
export DRILL_DATABASE=stock_watch_restore_drill_<UTC时间戳>
export CONFIRM_DROP_DATABASE="$DRILL_DATABASE"
bash deploy/opencloudos/scripts/restore-drill-postgres.sh
```

脚本只接受 `stock_watch_restore_drill_YYYYMMDDTHHMMSSZ` 格式，不会删除 `trading` 或其他数据库。

## 发布与回滚

```bash
bash deploy/opencloudos/scripts/deploy-release.sh \
  /root/releases/<release-id>.tar.gz \
  <backup-id>
```

发布使用不可变版本目录和原子 `current` 链接。AKShare 或 API 健康失败时自动切回上一应用版本。Flyway 如已迁移数据库，脚本不会猜测逆向 SQL；必须依据本次 PostgreSQL 备份评估恢复。

查看状态与脱敏日志：

```bash
systemctl status stock-watch-akshare stock-watch-api nginx
journalctl -u stock-watch-api -u stock-watch-akshare --since today
```

## IP 地址 HTTPS

确认公网 80/443 已放行、HTTP 页面可访问后执行：

```bash
bash deploy/opencloudos/scripts/issue-ip-certificate.sh
```

脚本安装 Certbot 5.4，使用 `shortlived` profile 和 `--ip-address` 申请受信任的约六天期证书，切换 Nginx HTTPS 配置，启用每六小时检查的续期 timer，并执行 dry-run。

验证：

```bash
systemctl list-timers stock-watch-cert-renew.timer
bash deploy/opencloudos/scripts/verify.sh
```

后续取得域名时，应迁移到域名证书并重新构建桌面端 API 地址。

## 桌面端连接

IP 证书生效后，在 Mac 上构建：

```bash
cd apps/desktop
VITE_API_URL=https://211.159.158.165 npm run tauri build
```

Web 前端由 Nginx 同源托管，不设置绝对 API 地址。
