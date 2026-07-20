use std::sync::Mutex;
use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    Manager, State, WindowEvent,
};
use tauri_plugin_global_shortcut::{GlobalShortcutExt, ShortcutState};

#[derive(Default)]
struct VisibilityState(Mutex<Vec<String>>);

#[derive(Default)]
struct StartupState(Mutex<Option<String>>);

#[tauri::command]
fn startup_warning(state: State<'_, StartupState>) -> Option<String> {
    state.0.lock().ok().and_then(|warning| warning.clone())
}

fn hide_all(app: &tauri::AppHandle) {
    let mut visible = Vec::new();
    for label in ["main", "ticker"] {
        if let Some(window) = app.get_webview_window(label) {
            if window.is_visible().unwrap_or(false) {
                visible.push(label.to_string());
                let _ = window.hide();
            }
        }
    }
    if let Ok(mut state) = app.state::<VisibilityState>().0.lock() {
        *state = visible;
    }
}

fn restore_windows(app: &tauri::AppHandle) {
    let labels = app.state::<VisibilityState>().0.lock()
        .map(|mut state| std::mem::take(&mut *state))
        .unwrap_or_default();
    let targets = if labels.is_empty() {
        vec!["main".to_string()]
    } else {
        labels
    };
    for label in targets {
        if let Some(window) = app.get_webview_window(&label) {
            let _ = window.show();
            if label == "main" {
                let _ = window.set_focus();
            }
        }
    }
}

fn toggle_windows(app: &tauri::AppHandle) {
    let any_visible = ["main", "ticker"]
        .iter()
        .filter_map(|label| app.get_webview_window(label))
        .any(|window| window.is_visible().unwrap_or(false));
    if any_visible {
        hide_all(app);
    } else {
        restore_windows(app);
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(VisibilityState::default())
        .manage(StartupState::default())
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .plugin(
            tauri_plugin_global_shortcut::Builder::new()
                .with_handler(|app, _shortcut, event| {
                    if event.state() == ShortcutState::Pressed {
                        toggle_windows(app);
                    }
                })
                .build(),
        )
        .invoke_handler(tauri::generate_handler![startup_warning])
        .setup(|app| {
            if let Err(error) = app.global_shortcut().register("CmdOrCtrl+Shift+H") {
                let message = format!("老板键注册失败，请检查快捷键冲突：{error}");
                if let Ok(mut warning) = app.state::<StartupState>().0.lock() {
                    *warning = Some(message);
                }
                hide_all(app.handle());
            }
            let show = MenuItem::with_id(app, "show", "显示主界面", true, None::<&str>)?;
            let ticker = MenuItem::with_id(app, "ticker", "显示盯盘小窗", true, None::<&str>)?;
            let hide = MenuItem::with_id(app, "hide", "隐藏全部", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "退出", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &ticker, &hide, &quit])?;
            TrayIconBuilder::new()
                .menu(&menu)
                .tooltip("股票盯盘助手")
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "show" => {
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                    "ticker" => {
                        if let Some(window) = app.get_webview_window("ticker") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                    "hide" => hide_all(app),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("启动桌面应用失败");
}
