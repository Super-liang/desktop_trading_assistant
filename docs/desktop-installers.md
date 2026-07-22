# 股票盯盘助手桌面安装包

## 当前发布配置

- 客户端 API：`https://211.159.158.165`
- macOS：Apple Silicon（arm64），DMG + APP ZIP
- Windows：x64，NSIS setup.exe（Windows 原生 workflow）
- 产物目录：`build/installers/<版本>/<平台>/`
- 完整性校验：每个产物目录中的 `SHA256SUMS.txt`

## macOS 本机构建

```bash
npm ci
npm run release:mac
```

安装时打开 DMG，将“股票盯盘助手”拖入“应用程序”。当前包使用 ad-hoc 签名，未经过 Apple 公证，因此 macOS 可能阻止首次打开。内部测试时可在“系统设置 → 隐私与安全性”中确认打开；公开发行前必须配置 Apple Developer ID 和公证凭据后重新构建。

校验下载文件：

```bash
cd build/installers/0.1.1/macos-arm64
shasum -a 256 -c SHA256SUMS.txt
```

## Windows 构建

权威 Windows 包通过 GitHub Actions 的 `Desktop installers` workflow 在 `windows-latest` 生成。手动触发可生成临时 Artifact；推送与应用版本一致的 `v*` 标签会在 macOS、Windows 均成功后创建 GitHub Release。Windows 正式资产为经过 PE x64 校验的 NSIS EXE 和哈希清单，不依赖 WiX MSI。

Mac 可安装 `cargo-xwin`、Homebrew LLVM 与 NSIS 后尝试交叉生成 NSIS 测试包：

```bash
cargo install cargo-xwin --locked
brew install llvm nsis
npm ci
npm run release:windows:cross
```

交叉构建产物仍必须在 Windows x64 真机验证安装、启动、登录、行情和透明小窗。当前 Windows 包未进行 Authenticode 签名，SmartScreen 可能显示“未知发布者”；公开发行前必须配置代码签名证书。

PowerShell 校验示例：

```powershell
Get-FileHash .\StockTradingAssistant_0.1.1_windows-x64-setup.exe -Algorithm SHA256
```

## 更换 API 地址

发布 API 必须使用 HTTPS，可在构建时覆盖。版本号始终读取应用配置，发布前需同步修改 `apps/desktop/package.json` 和 `apps/desktop/src-tauri/tauri.conf.json`：

```bash
VITE_API_URL=https://your-domain.example npm run release:mac
```

构建脚本会检查编译后的前端资源；未包含指定 API 或仍包含 localhost API 时会直接失败。

## 正式发布前检查

1. 使用稳定域名和可信 TLS 证书替换 IP 地址。
2. 为 macOS 配置 Developer ID 签名和 Apple notarization。
3. 为 Windows 配置 Authenticode 代码签名。
4. 分别在干净的 macOS 与 Windows 真机测试安装、卸载、登录、行情、持仓盈亏、老板键和透明小窗。
5. 按 `SHA256SUMS.txt` 校验上传和下载后的安装包。

## 发布验证记录

- `v0.1.0`：macOS DMG/APP ZIP 构建成功；Windows 原生 Runner 已成功生成 NSIS EXE，但随后 WiX MSI `light.exe` 失败，因此该次 workflow 未创建 Release。
- `v0.1.1`：macOS ARM64 构建用时 4分27秒，Windows x64 NSIS 构建用时 10分54秒；生产 API、DMG、PE x64、哈希与 Artifact 上传均通过。Release 已发布 5 项资产，两份 SHA-256 清单均经下载复核通过。
- `v0.1.1` 首次自动 Release step 因 YAML 折叠导致标签参数带前导空格而失败；修复已推送到 `main`，本次复用同一运行的已验证 Artifact 通过仓库发布脚本完成 Release，未重新编译或替换版本标签。
- 尚未执行：Windows 真机安装、卸载和业务功能验收。
- 两个平台均未配置正式发行证书；Release 属于预览/测试版本，安装时可能出现系统安全警告。
