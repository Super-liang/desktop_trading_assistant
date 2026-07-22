# 股票盯盘助手桌面安装包

## 当前发布配置

- 客户端 API：`https://211.159.158.165`
- macOS：Apple Silicon（arm64），DMG + APP ZIP
- Windows：x64，NSIS setup.exe + MSI（Windows 原生 workflow）
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
cd build/installers/0.1.0/macos-arm64
shasum -a 256 -c SHA256SUMS.txt
```

## Windows 构建

权威 Windows 包通过 GitHub Actions 的 `Desktop installers` 手动 workflow 在 `windows-latest` 生成。该流程同时产出 NSIS EXE、MSI 和哈希清单。

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
Get-FileHash .\StockTradingAssistant_0.1.0_windows-x64-setup.exe -Algorithm SHA256
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

## 本次 0.1.0 验证记录

- macOS：DMG 与 APP ZIP 已生成；云 API、SHA-256、DMG、arm64 架构、ad-hoc 签名结构及本机启动已验证。
- Windows：Mac 交叉构建的 x64 NSIS 已生成；云 API、SHA-256、NSIS PE 格式、内部应用 x86-64 与 Windows GUI subsystem 已验证。
- 尚未执行：Windows 原生 workflow 的 MSI/NSIS 构建，以及 Windows 真机安装和业务功能验收。
- 两个平台均未配置正式发行证书；这些文件只作为内部测试包，不应直接公开发布。
