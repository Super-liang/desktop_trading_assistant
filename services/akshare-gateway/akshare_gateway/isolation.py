from __future__ import annotations

from multiprocessing import get_context
from multiprocessing.connection import Connection
from typing import Any, Callable


def _worker(
    sender: Connection,
    operation: str,
    source: str | None,
    instrument_id: str | None,
    request_timeout_seconds: float,
) -> None:
    """子进程只返回 DataFrame 或脱敏异常类型。"""
    try:
        from .app import (
            load_akshare_frame,
            load_instrument_frame,
            load_market_frame,
            load_single_frame,
        )
        from .http_timeout import install_default_requests_timeout
        from .sources import MarketSource, SingleSource

        install_default_requests_timeout(request_timeout_seconds)
        if operation == "legacy":
            result = load_akshare_frame()
        elif operation == "market":
            result = load_market_frame(MarketSource(source))
        elif operation == "single":
            result = load_single_frame(SingleSource(source), instrument_id or "")
        elif operation == "instruments":
            result = load_instrument_frame()
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
    instrument_id: str | None = None,
    timeout_seconds: float,
    _worker_target: Callable[..., None] = _worker,
) -> Any:
    """用可终止子进程为一次完整 AKShare 操作设置硬截止时间。"""
    context = get_context("spawn")
    receiver, sender = context.Pipe(duplex=False)
    process = context.Process(
        target=_worker_target,
        args=(sender, operation, source, instrument_id, timeout_seconds),
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
