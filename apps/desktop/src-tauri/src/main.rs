// Windows 发布构建使用 GUI 子系统，避免启动桌面应用时额外弹出控制台窗口。
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    trading_assistant_lib::run();
}
