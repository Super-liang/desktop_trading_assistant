from __future__ import annotations

from multiprocessing import get_context
from multiprocessing.connection import Connection
from typing import Any, Callable


def _worker(
    sender: Connection,
    operation: str,
    source: str | None,
    market: str | None,
    instrument_id: str | None,
    request_timeout_seconds: float,
) -> None:
    """子进程只返回 DataFrame 或脱敏异常类型。"""
    try:
        from .app import (
            load_akshare_frame,
            load_instrument_frame,
            load_market_frame,
            load_index_frames,
            load_a_share_trade_dates,
            load_fund_nav_frame,
            load_fund_history_frame,
            load_single_frame,
        )
        from .contracts import CatalogMarket
        from .http_timeout import install_default_requests_timeout
        from .sources import MarketSource, SingleSource
        from .us_position_quotes import fetch_us_position_quotes

        install_default_requests_timeout(request_timeout_seconds)
        if operation == "legacy":
            result = load_akshare_frame()
        elif operation == "market":
            result = load_market_frame(
                MarketSource(source), CatalogMarket(market or "A_SHARE"))
        elif operation == "single":
            result = load_single_frame(SingleSource(source), instrument_id or "")
        elif operation == "us_position_quotes":
            result = fetch_us_position_quotes(
                (instrument_id or "").split(","),
                timeout_seconds=request_timeout_seconds,
            )
        elif operation == "instruments":
            result = load_instrument_frame(CatalogMarket(source or "A_SHARE"))
        elif operation == "indices":
            result = load_index_frames(MarketSource(source))
        elif operation == "a_share_calendar":
            result = load_a_share_trade_dates()
        elif operation == "fund_nav":
            result = load_fund_nav_frame()
        elif operation == "fund_history":
            result = load_fund_history_frame(instrument_id or "")
        else:
            raise ValueError("不支持的 AKShare 隔离操作")
        sender.send(("OK", result))
    except BaseException as exception:
        sender.send(("ERROR", type(exception).__name__))
    finally:
        sender.close()


def run_isolated(
    operation: str,
    *,
    source: str | None = None,
    market: str | None = None,
    instrument_id: str | None = None,
    timeout_seconds: float,
    request_timeout_seconds: float | None = None,
    _worker_target: Callable[..., None] = _worker,
) -> Any:
    """用可终止子进程为一次完整 AKShare 操作设置硬截止时间。"""
    context = get_context("spawn")
    receiver, sender = context.Pipe(duplex=False)
    process = context.Process(
        target=_worker_target,
        args=(
            sender, operation, source, market, instrument_id,
            request_timeout_seconds or timeout_seconds,
        ),
        daemon=True,
    )
    process.start()
    sender.close()
    try:
        if not receiver.poll(timeout_seconds):
            raise TimeoutError("AKShare 上游操作超过总时限")
        status, payload = receiver.recv()
        if status != "OK":
            raise RuntimeError(f"AKShare 上游调用失败: {payload}")
        return payload
    finally:
        receiver.close()
        if process.is_alive():
            process.terminate()
        process.join(timeout=5)
        if process.is_alive():
            process.kill()
            process.join(timeout=1)
