from __future__ import annotations

from threading import Lock

import requests

_lock = Lock()
_installed = False
_default_seconds = 30.0
_original_request = requests.sessions.Session.request


def install_default_requests_timeout(seconds: float) -> None:
    """为 AKShare 内部未显式设置 timeout 的 requests 调用增加进程级上限。"""
    global _default_seconds, _installed
    with _lock:
        _default_seconds = seconds
        if _installed:
            return

        def request_with_timeout(session, method, url, **kwargs):
            if kwargs.get("timeout") is None:
                kwargs["timeout"] = _default_seconds
            return _original_request(session, method, url, **kwargs)

        requests.sessions.Session.request = request_with_timeout
        _installed = True
