use serde::Serialize;
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Mutex,
};
use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    Emitter, Manager, State, WebviewUrl, WebviewWindowBuilder, WindowEvent,
};
use tauri_plugin_global_shortcut::{GlobalShortcutExt, ShortcutState};
use tauri_plugin_window_state::StateFlags;

#[derive(Default)]
struct VisibilityState(Mutex<Vec<String>>);

#[derive(Default)]
struct StartupState(Mutex<Option<String>>);

#[derive(Default)]
struct AuthenticationState(AtomicBool);

const WINDOW_VISIBILITY_EVENT: &str = "window-visibility-changed";

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct WindowVisibilityPayload {
    label: String,
    visible: bool,
}

#[derive(Debug, PartialEq, Eq)]
enum TickerToggleAction {
    Reject,
    Create,
    Destroy,
}

fn ticker_access(authenticated: bool) -> bool {
    authenticated
}

fn ticker_toggle_action(authenticated: bool, exists: bool) -> TickerToggleAction {
    if !authenticated {
        TickerToggleAction::Reject
    } else if exists {
        TickerToggleAction::Destroy
    } else {
        TickerToggleAction::Create
    }
}

fn effective_visibility(visible: bool, minimized: bool) -> bool {
    visible && !minimized
}

fn persisted_window_state_flags() -> StateFlags {
    StateFlags::SIZE
        | StateFlags::POSITION
        | StateFlags::MAXIMIZED
        | StateFlags::DECORATIONS
        | StateFlags::FULLSCREEN
}

fn is_authenticated(app: &tauri::AppHandle) -> bool {
    app.state::<AuthenticationState>().0.load(Ordering::Acquire)
}

fn emit_visibility(app: &tauri::AppHandle, label: &str, visible: bool) {
    let payload = WindowVisibilityPayload {
        label: label.to_string(),
        visible,
    };
    let _ = app.emit(WINDOW_VISIBILITY_EVENT, payload);
}

fn create_ticker_window(app: &tauri::AppHandle) -> tauri::Result<()> {
    if let Some(window) = app.get_webview_window("ticker") {
        window.show()?;
        window.set_focus()?;
        emit_visibility(app, "ticker", true);
        return Ok(());
    }
    let window = WebviewWindowBuilder::new(
        app,
        "ticker",
        WebviewUrl::App("index.html?view=ticker".into()),
    )
    .title("盯盘小窗")
    .inner_size(720.0, 340.0)
    .min_inner_size(420.0, 200.0)
    .decorations(false)
    .transparent(true)
    .always_on_top(true)
    .skip_taskbar(true)
    .visible(true)
    .build()?;
    window.set_focus()?;
    emit_visibility(app, "ticker", true);
    Ok(())
}

fn spawn_create_ticker(app: &tauri::AppHandle) {
    let handle = app.clone();
    std::thread::spawn(move || {
        if let Err(error) = create_ticker_window(&handle) {
            eprintln!("创建透明小窗失败：{error}");
        }
    });
}

fn destroy_ticker(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("ticker") {
        emit_visibility(app, "ticker", false);
        let _ = window.destroy();
    }
}

#[tauri::command]
async fn toggle_ticker_window(app: tauri::AppHandle) -> Result<(), String> {
    match ticker_toggle_action(
        is_authenticated(&app),
        app.get_webview_window("ticker").is_some(),
    ) {
        TickerToggleAction::Reject => Err("请先登录后再打开盯盘小窗".to_string()),
        TickerToggleAction::Destroy => {
            destroy_ticker(&app);
            Ok(())
        }
        TickerToggleAction::Create => create_ticker_window(&app).map_err(|error| error.to_string()),
    }
}

#[tauri::command]
fn set_authenticated(
    app: tauri::AppHandle,
    state: State<'_, AuthenticationState>,
    authenticated: bool,
) {
    state.0.store(authenticated, Ordering::Release);
    if !authenticated {
        destroy_ticker(&app);
    }
}

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
                if label == "ticker" {
                    emit_visibility(app, label, false);
                    let _ = window.destroy();
                } else {
                    emit_visibility(app, label, false);
                    let _ = window.hide();
                }
            }
        }
    }
    if let Ok(mut state) = app.state::<VisibilityState>().0.lock() {
        *state = visible;
    }
}

fn restore_windows(app: &tauri::AppHandle) {
    let labels = app
        .state::<VisibilityState>()
        .0
        .lock()
        .map(|mut state| std::mem::take(&mut *state))
        .unwrap_or_default();
    let targets = if labels.is_empty() {
        vec!["main".to_string()]
    } else {
        labels
    };
    let authenticated = is_authenticated(app);
    let mut restored = false;
    for label in targets {
        if label == "ticker" && !ticker_access(authenticated) {
            continue;
        }
        if label == "ticker" {
            spawn_create_ticker(app);
            restored = true;
        } else if let Some(window) = app.get_webview_window(&label) {
            let _ = window.show();
            emit_visibility(app, &label, true);
            restored = true;
            let _ = window.set_focus();
        }
    }
    if !restored && !authenticated {
        if let Some(window) = app.get_webview_window("main") {
            let _ = window.show();
            let _ = window.set_focus();
            emit_visibility(app, "main", true);
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
        .manage(AuthenticationState::default())
        .plugin(
            tauri_plugin_window_state::Builder::default()
                .with_state_flags(persisted_window_state_flags())
                .build(),
        )
        .plugin(
            tauri_plugin_global_shortcut::Builder::new()
                .with_handler(|app, _shortcut, event| {
                    if event.state() == ShortcutState::Pressed {
                        toggle_windows(app);
                    }
                })
                .build(),
        )
        .invoke_handler(tauri::generate_handler![
            startup_warning,
            set_authenticated,
            toggle_ticker_window
        ])
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
                            emit_visibility(app, "main", true);
                        }
                    }
                    "ticker" => {
                        if ticker_access(is_authenticated(app)) {
                            spawn_create_ticker(app);
                        } else if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                            emit_visibility(app, "main", true);
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
                if window.label() == "ticker" {
                    emit_visibility(window.app_handle(), "ticker", false);
                } else {
                    api.prevent_close();
                    emit_visibility(window.app_handle(), window.label(), false);
                    let _ = window.hide();
                }
            } else if let WindowEvent::Resized(_) = event {
                let visible = window.is_visible().unwrap_or(false);
                let minimized = window.is_minimized().unwrap_or(false);
                emit_visibility(
                    window.app_handle(),
                    window.label(),
                    effective_visibility(visible, minimized),
                );
            }
        })
        .run(tauri::generate_context!())
        .expect("启动桌面应用失败");
}

#[cfg(test)]
mod tests {
    use super::{
        effective_visibility, persisted_window_state_flags, ticker_access, ticker_toggle_action,
        StateFlags, TickerToggleAction,
    };

    #[test]
    fn unauthenticated_session_cannot_show_ticker_from_native_entry_points() {
        assert!(!ticker_access(false));
    }

    #[test]
    fn authenticated_session_can_show_ticker_from_native_entry_points() {
        assert!(ticker_access(true));
    }

    #[test]
    fn window_state_keeps_layout_but_never_restores_visibility() {
        let flags = persisted_window_state_flags();

        assert!(flags.contains(StateFlags::SIZE | StateFlags::POSITION));
        assert!(!flags.contains(StateFlags::VISIBLE));
    }

    #[test]
    fn ticker_is_created_only_for_authenticated_user() {
        assert_eq!(
            ticker_toggle_action(false, false),
            TickerToggleAction::Reject
        );
        assert_eq!(
            ticker_toggle_action(true, false),
            TickerToggleAction::Create
        );
    }

    #[test]
    fn existing_ticker_is_destroyed_on_toggle() {
        assert_eq!(
            ticker_toggle_action(true, true),
            TickerToggleAction::Destroy
        );
    }

    #[test]
    fn minimized_window_is_not_effectively_visible() {
        assert!(effective_visibility(true, false));
        assert!(!effective_visibility(true, true));
        assert!(!effective_visibility(false, false));
    }
}
