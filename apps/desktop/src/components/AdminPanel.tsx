import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { api } from "../lib/api";

export function AdminPanel({ onBack }: { onBack: () => void }) {
  const [query, setQuery] = useState("");
  const users = useQuery({ queryKey: ["admin-users", query], queryFn: () => api.users(query) });
  const audits = useQuery({ queryKey: ["admin-audits"], queryFn: api.audits });
  return (
    <main className="admin-page">
      <header><button className="icon-button" onClick={onBack}><ArrowLeft /></button>
        <div><p className="eyebrow">ADMIN CONSOLE</p><h1>用户管理</h1></div></header>
      <div className="admin-toolbar"><ShieldCheck size={18} /><span>管理员默认不可查看用户持仓明细</span>
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="搜索邮箱或昵称" /></div>
      <section className="admin-table">
        <div className="admin-row admin-head"><span>用户</span><span>角色</span><span>状态</span><span>注册时间</span><span /></div>
        {users.data?.content.map((user) => (
          <div className="admin-row" key={user.id}>
            <span><strong>{user.displayName}</strong><small>{user.email}</small></span>
            <span>{user.role}</span><span className={user.status === "ACTIVE" ? "up" : "muted"}>{user.status}</span>
            <span>{new Date(user.createdAt).toLocaleDateString("zh-CN")}</span>
            <span><button className="secondary-button" onClick={async () => {
              await api.setUserStatus(user.id, user.status === "ACTIVE" ? "DISABLED" : "ACTIVE");
              await users.refetch();
            }}>{user.status === "ACTIVE" ? "禁用" : "启用"}</button></span>
          </div>
        ))}
      </section>
      <div className="section-title admin-audit-title"><div><p className="eyebrow">AUDIT LOG</p>
        <h2>管理审计</h2></div><span>{audits.data?.content.length ?? 0} 条</span></div>
      <section className="admin-table">
        <div className="admin-row audit-row admin-head"><span>动作</span><span>目标用户</span><span>结果</span><span>时间</span></div>
        {audits.data?.content.map((audit) => (
          <div className="admin-row audit-row" key={audit.id}>
            <span>{audit.action}</span><span>{audit.targetUserId ?? "—"}</span>
            <span className="up">{audit.result}</span>
            <span>{new Date(audit.createdAt).toLocaleString("zh-CN")}</span>
          </div>
        ))}
      </section>
    </main>
  );
}
