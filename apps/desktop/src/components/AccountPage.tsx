import { KeyRound, LogOut, Shield, UserRound, UserX } from "lucide-react";

export function AccountPage({ isAdmin, onChangePassword, onLogout, onLogoutAll,
  onDeleteAccount, onOpenAdmin }: {
  isAdmin: boolean;
  onChangePassword: () => void;
  onLogout: () => void;
  onLogoutAll: () => void;
  onDeleteAccount: () => void;
  onOpenAdmin: () => void;
}) {
  return <main className="account-page">
    <header><p className="eyebrow">ACCOUNT</p><h1>我的</h1>
      <p>管理当前账号、安全设置和登录设备</p></header>
    <section className="account-card">
      <div className="account-card-title"><UserRound size={21} /><div><h2>账号与安全</h2>
        <p>密码和后台管理</p></div></div>
      <div className="account-actions">
        <button onClick={onChangePassword}><KeyRound size={19} /><span><strong>修改密码</strong>
          <small>更新当前账号的登录密码</small></span></button>
        {isAdmin && <button onClick={onOpenAdmin}><Shield size={19} /><span><strong>用户管理</strong>
          <small>查看用户、持仓收益和操作审计</small></span></button>}
      </div>
    </section>
    <section className="account-card">
      <div className="account-card-title"><Shield size={21} /><div><h2>登录设备</h2>
        <p>管理当前及其他设备上的会话</p></div></div>
      <div className="account-actions">
        <button onClick={onLogout}><LogOut size={19} /><span><strong>退出登录</strong>
          <small>仅退出当前设备</small></span></button>
        <button onClick={onLogoutAll}><Shield size={19} /><span><strong>退出全部设备</strong>
          <small>使所有设备的刷新会话失效</small></span></button>
      </div>
    </section>
    <section className="account-card danger-zone">
      <div className="account-card-title"><UserX size={21} /><div><h2>危险操作</h2>
        <p>注销后账号和相关数据不可恢复</p></div></div>
      <button className="danger-account-action" onClick={onDeleteAccount}><UserX size={19} />
        <span><strong>注销账号</strong><small>需要当前密码和二次确认</small></span></button>
    </section>
  </main>;
}
