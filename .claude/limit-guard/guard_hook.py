"""PreToolUse hook: stop all work when the 5-hour (or 7-day) session limit reaches 98%.

Allowed while limited, so Claude can park safely: scheduling the resume (CronCreate/CronList/
ScheduleWakeup) and editing the progress notes MEMORY.md / docs/PROGRESS.md. Everything else is
denied with instructions to schedule "Kaldığın yerden profesyonel bir şekilde devam et" one minute
after the reset time. A stale state file (no sample for 10 minutes) never blocks.
"""
import json
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent
STATE = HERE / "state.json"
STALE_AFTER_S = 600
ALLOWED_TOOLS = {"CronCreate", "CronList", "CronDelete", "ScheduleWakeup", "ToolSearch"}
ALLOWED_NOTE_FILES = ("MEMORY.md", "PROGRESS.md")


def limited_window(state, now):
    for name in ("five_hour", "seven_day"):
        w = state.get(name) or {}
        pct, reset = w.get("used_percentage"), w.get("resets_at")
        if pct is None or pct < state.get("threshold", 98.0):
            continue
        if reset and reset <= now:  # window already reset; sample is outdated
            continue
        return name, pct, reset
    return None


def main():
    event = json.loads(sys.stdin.buffer.read().decode("utf-8", errors="replace"))
    if not STATE.exists():
        return
    state = json.loads(STATE.read_text(encoding="utf-8"))
    now = time.time()
    if now - state.get("updated_at", 0) > STALE_AFTER_S:
        return
    hit = limited_window(state, now)
    if not hit:
        return

    tool = event.get("tool_name", "")
    path = str((event.get("tool_input") or {}).get("file_path", ""))
    if tool in ALLOWED_TOOLS or (tool in {"Edit", "Write"} and Path(path).name in ALLOWED_NOTE_FILES):
        return

    name, pct, reset = hit
    resume = datetime.fromtimestamp(reset) + timedelta(minutes=1) if reset else None
    when = resume.strftime("%Y-%m-%d %H:%M") if resume else "bilinmiyor"
    cron = f"{resume.minute} {resume.hour} {resume.day} {resume.month} *" if resume else "?"
    reason = (
        f"SESSION LIMIT GUARD: {name} usage {pct:.1f}% >= 98%. Stop all work now. "
        f"1) Write current state/next step to MEMORY.md and docs/PROGRESS.md. "
        f"2) CronCreate one-shot (recurring=false) cron='{cron}' (= {when}, reset+1 min) with prompt "
        f"'Kaldığın yerden profesyonel bir şekilde devam et'. 3) End the turn and wait; do not ask the user."
    )
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": reason,
    }}))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - a broken guard must be visible, not silently disabled
        print(f"limit guard error: {exc!r}", file=sys.stderr)
    sys.exit(0)
