import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ChevronLeft, ChevronRight, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { money, percent } from "../lib/format";
import type { AdminUser, PerformanceStatus } from "../types";

const actionLabel: Record<string, string> = {
  PORTFOLIO_CREATED: "添加持仓",
  PORTFOLIO_UPDATED: "修改持仓",
  PORTFOLIO_DELETED: "删除持仓",
  PASSWORD_CHANGED: "修改密码",
};
const statusLabel: Record<PerformanceStatus, string> = {
  COMPLETE: "数据完整", PARTIAL: "部分数据", UNAVAILABLE: "暂不可用", ACCUMULATING: "数据积累中",
};

function loginTime(value?: string) {
  return value ? new Date(value).toLocaleString("zh-CN") : "尚未登录";
}

function AdminUserDetail({ user, onBack }: { user: AdminUser; onBack: () => void }) {
  const [action, setAction] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const overview = useQuery({
    queryKey: ["admin-user-overview", user.id], queryFn: () => api.adminUserOverview(user.id),
  });
  const holdings = useQuery({
    queryKey: ["admin-user-holdings", user.id], queryFn: () => api.adminUserHoldings(user.id),
  });
  const audits = useQuery({
    queryKey: ["admin-user-audits", user.id, action, from, to, page],
    queryFn: () => api.adminUserAudits(user.id, {
      action: action || undefined,
      from: from ? new Date(`${from}T00:00:00+08:00`).toISOString() : undefined,
      to: to ? new Date(`${to}T23:59:59+08:00`).toISOString() : undefined,
      page, size: 20,
    }),
  });
  useEffect(() => setPage(0), [action, from, to]);

  const requestError = overview.error || holdings.error || audits.error;
  const performance = overview.data?.performance;
  return <main className="admin-page admin-detail-page">
    <header><button className="icon-button" aria-label="返回用户列表" onClick={onBack}><ArrowLeft /></button>
      <div><p className="eyebrow">USER INSIGHTS</p><h1>{user.displayName}</h1>
        <p className="admin-subtitle">{user.email} · 最后登录 {loginTime(
          overview.data?.user.lastLoginAt ?? user.lastLoginAt)}</p></div></header>
    <div className={`admin-error-slot ${requestError ? "form-error admin-error" : ""}`}>
      {requestError ? requestError instanceof Error ? requestError.message : "用户详情加载失败" : null}
    </div>
    <section className="admin-performance-grid">
      <article><small>日收益</small><strong className={(performance?.dailyProfit ?? 0) >= 0 ? "up" : "down"}>
        {performance?.dailyProfit == null ? "--" : `${performance.dailyProfit >= 0 ? "+" : "-"}¥ ${money(Math.abs(performance.dailyProfit))}`}</strong>
        <span>{performance?.dailyReturnPercent == null ? "--" : percent(performance.dailyReturnPercent)}</span></article>
      <article><small>本年收益</small><strong className={(performance?.yearProfit ?? 0) >= 0 ? "up" : "down"}>
        {performance?.yearProfit == null ? "--" : `${performance.yearProfit >= 0 ? "+" : "-"}¥ ${money(Math.abs(performance.yearProfit))}`}</strong>
        <span>{performance?.yearReturnPercent == null ? "--" : percent(performance.yearReturnPercent)}</span></article>
      <article><small>年化收益率</small><strong>{performance?.annualizedReturnPercent == null ? "--"
        : percent(performance.annualizedReturnPercent)}</strong>
        <span>{performance?.status ? statusLabel[performance.status] : "等待统计"}</span></article>
      <article><small>证券数</small><strong>{overview.data?.holdingCount ?? "--"}</strong>
        <span>{performance?.statisticsStartDate ? `统计始于 ${performance.statisticsStartDate}` : "尚无统计起始日"}</span></article>
    </section>
    <section className="admin-detail-section holdings-section">
      <div className="section-title"><div><p className="eyebrow">HOLDINGS</p><h2>持仓证券清单</h2></div>
        <span>仅展示隐私化证券信息</span></div>
      <div className="admin-holding-list">
        {holdings.data?.content.map((holding) => <article key={holding.instrumentId}>
          <span><strong>{holding.displayName}</strong><small>{holding.instrumentId}</small></span>
          <span>{holding.exchange}</span>
          <span className={holding.quoteAvailable ? "up" : "muted"}>{holding.quoteAvailable ? "行情可用" : "行情缺失"}</span>
        </article>)}
        {holdings.isSuccess && !holdings.data.content.length && <div className="empty-state">该用户暂无持仓证券</div>}
      </div>
    </section>
    <section className="admin-detail-section audit-section">
      <div className="section-title"><div><p className="eyebrow">USER AUDIT</p><h2>关键操作审计</h2></div>
        <span>{audits.data?.totalElements ?? 0} 条</span></div>
      <div className="audit-filters">
        <label>操作类型<select aria-label="操作类型" value={action}
          onChange={(event) => setAction(event.target.value)}>
          <option value="">全部操作</option>{Object.entries(actionLabel).map(([value, label]) =>
            <option value={value} key={value}>{label}</option>)}</select></label>
        <label>开始日期<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
        <label>结束日期<input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
      </div>
      <div className="admin-audit-list">
        {audits.data?.content.map((audit) => <article key={audit.id}>
          <span><strong>{actionLabel[audit.action] ?? audit.action}</strong>
            <small>{audit.instrumentName ?? "账户安全操作"}{audit.instrumentId ? ` · ${audit.instrumentId}` : ""}</small></span>
          <span className={audit.result === "SUCCESS" ? "up" : "down"}>{audit.result}</span>
          <time>{new Date(audit.createdAt).toLocaleString("zh-CN")}</time>
        </article>)}
      </div>
      <div className="pagination">
        <button className="icon-button" aria-label="上一页" disabled={page <= 0} onClick={() => setPage((value) => value - 1)}>
          <ChevronLeft /></button><span>第 {page + 1} / {Math.max(audits.data?.totalPages ?? 1, 1)} 页</span>
        <button className="icon-button" aria-label="下一页" disabled={page + 1 >= (audits.data?.totalPages ?? 1)}
          onClick={() => setPage((value) => value + 1)}><ChevronRight /></button>
      </div>
    </section>
  </main>;
}

export function AdminPanel({ onBack }: { onBack: () => void }) {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [selected, setSelected] = useState<AdminUser | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [query]);
  const users = useQuery({
    queryKey: ["admin-users", debouncedQuery], queryFn: () => api.users(debouncedQuery),
  });
  if (selected) return <AdminUserDetail user={selected} onBack={() => setSelected(null)} />;
  return <main className="admin-page admin-users-page">
    <header><button className="icon-button" aria-label="返回首页" onClick={onBack}><ArrowLeft /></button>
      <div><p className="eyebrow">ADMIN CONSOLE</p><h1>用户管理</h1></div></header>
    <div className="admin-toolbar"><ShieldCheck size={18} /><span>用户详情遵循隐私最小化原则</span>
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索邮箱或昵称" /></div>
    {users.isError && <div className="form-error admin-error">{users.error instanceof Error
      ? users.error.message : "用户列表加载失败"}</div>}
    <section className="admin-table admin-users-table">
      <div className="admin-row admin-head"><span>用户</span><span>角色</span><span>状态</span>
        <span>最后登录</span><span>注册时间</span><span /></div>
      {users.data?.content.map((user) => <div className="admin-row" key={user.id}>
        <span><strong>{user.displayName}</strong><small>{user.email}</small></span>
        <span>{user.role}</span><span className={user.status === "ACTIVE" ? "up" : "muted"}>{user.status}</span>
        <span>{loginTime(user.lastLoginAt)}</span>
        <span>{new Date(user.createdAt).toLocaleDateString("zh-CN")}</span>
        <span className="admin-row-actions"><button className="secondary-button"
          aria-label={`查看 ${user.displayName}`} onClick={() => setSelected(user)}>查看</button>
          <button className="secondary-button" onClick={async () => {
            await api.setUserStatus(user.id, user.status === "ACTIVE" ? "DISABLED" : "ACTIVE");
            await users.refetch();
          }}>{user.status === "ACTIVE" ? "禁用" : "启用"}</button></span>
      </div>)}
    </section>
  </main>;
}
