"""Claude Code status line + session-limit sampler.

Claude Code runs this every `refreshInterval` seconds (60) and on events, passing session JSON on
stdin. We persist the rate-limit windows to state.json (read by guard_hook.py) and append one
sample per minute to usage.log, then print a compact status line.
"""
import json
import sys
import time
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
STATE = HERE / "state.json"
LOG = HERE / "usage.log"
THRESHOLD = 98.0


def to_epoch_seconds(value):
    """Accept epoch seconds, epoch millis or an ISO-8601 string; return epoch seconds or None."""
    if value is None:
        return None
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
        except ValueError:
            return None
    value = float(value)
    return value / 1000 if value > 1e12 else value


def window(data, name):
    w = (data.get("rate_limits") or {}).get(name) or {}
    pct = w.get("used_percentage")
    return (float(pct) if pct is not None else None), to_epoch_seconds(w.get("resets_at"))


def fmt_reset(epoch):
    return datetime.fromtimestamp(epoch).strftime("%H:%M") if epoch else "?"


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    try:
        data = json.loads(sys.stdin.buffer.read().decode("utf-8", errors="replace"))
    except json.JSONDecodeError:
        data = {}
    five_pct, five_reset = window(data, "five_hour")
    week_pct, week_reset = window(data, "seven_day")
    now = time.time()

    # Some event-driven refreshes omit rate_limits; keep the last known sample instead of
    # silently lifting an active block.
    if STATE.exists():
        try:
            prev = json.loads(STATE.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            prev = {}
        if five_pct is None:
            five_pct = prev.get("five_hour", {}).get("used_percentage")
            five_reset = prev.get("five_hour", {}).get("resets_at")
        if week_pct is None:
            week_pct = prev.get("seven_day", {}).get("used_percentage")
            week_reset = prev.get("seven_day", {}).get("resets_at")

    state = {
        "updated_at": now,
        "five_hour": {"used_percentage": five_pct, "resets_at": five_reset},
        "seven_day": {"used_percentage": week_pct, "resets_at": week_reset},
        "threshold": THRESHOLD,
    }
    tmp = STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(state), encoding="utf-8")
    tmp.replace(STATE)  # atomic so the hook never reads a half-written file

    stamp = datetime.fromtimestamp(now).strftime("%Y-%m-%d %H:%M")
    last = LOG.read_text(encoding="utf-8").splitlines()[-1:] if LOG.exists() else []
    if not last or not last[0].startswith(stamp):  # one line per minute
        with LOG.open("a", encoding="utf-8") as f:
            f.write(f"{stamp} 5h={five_pct} reset={fmt_reset(five_reset)} 7d={week_pct}\n")

    ctx = (data.get("context_window") or {}).get("used_percentage")
    parts = []
    if five_pct is not None:
        flag = " DUR" if five_pct >= THRESHOLD else ""
        parts.append(f"5s limit %{five_pct:.0f} (sıfırlanma {fmt_reset(five_reset)}){flag}")
    else:
        parts.append("5s limit: veri yok")
    if week_pct is not None:
        parts.append(f"7g %{week_pct:.0f}")
    if ctx is not None:
        parts.append(f"bağlam %{float(ctx):.0f}")
    print(" | ".join(parts))


if __name__ == "__main__":
    main()
