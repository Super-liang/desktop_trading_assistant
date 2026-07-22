import { Component, type ErrorInfo, type ReactNode } from "react";

type Props = {
  children: ReactNode;
  onReload?: () => void;
};

type State = { error: Error | null };

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // 保留技术细节供安装版 WebView 日志诊断，恢复界面不向用户暴露内部信息。
    console.error("应用界面渲染失败", error, info.componentStack);
  }

  private reload = () => {
    if (this.props.onReload) this.props.onReload();
    else window.location.reload();
  };

  render() {
    if (!this.state.error) return this.props.children;
    return <main className="fatal-error" role="alert">
      <div>
        <p className="eyebrow">RECOVERY MODE</p>
        <h1>界面暂时无法显示</h1>
        <p>应用已拦截异常，没有退出登录。请重新加载后继续使用。</p>
        <button className="primary-button small" type="button" onClick={this.reload}>重新加载</button>
      </div>
    </main>;
  }
}
