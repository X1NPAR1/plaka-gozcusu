---
name: usage-guard
description: Keep a long Claude Code task inside the session usage limit. Reads the "Current session" percentage and reset time from claude.ai/settings/usage in Chrome at the start of every minute (strict) and around long steps, logs it, stops safely at 98%, sleeps in-session until one minute after the reset, then resumes with "Kaldığın yerden profesyonel bir şekilde devam et". Use when the user asks to watch, track or guard the Claude session limit, or to keep working unattended across a limit reset.
---

# Usage guard

Protocol for unattended work that must not run into the Claude session limit.
It needs Claude in Chrome (`mcp__claude-in-chrome__*`) and a signed-in claude.ai.

## 1. Start (beginning of the next phase or task)

1. Load the Chrome tools in one ToolSearch call:
   `select:mcp__claude-in-chrome__tabs_context_mcp,mcp__claude-in-chrome__navigate,mcp__claude-in-chrome__javascript_tool,mcp__claude-in-chrome__tabs_close_mcp`
2. `navigate` (no tabId) to `https://claude.ai/new#settings/usage`. This opens the
   session's own tab with the user's sign-in; never reuse a tab from another session.
3. Read once (step 2) and tell the user the current percentage and reset time.
4. Write these rules into the project's memory/progress file so a resumed turn knows them.

## 2. Check (EVERY MINUTE — strict rule, never skip, never forget)

**STRICT RULE — DO NOT FORGET:** while working, check usage **at the start of every minute**:
before EVERY tool call, if one minute or more has passed since the last logged check, check first.
Also check before and after every long tool call (builds, tests, uploads, CI waits). Forgetting a
check is a failure of this skill; there is no "too busy" exception. Do not rely on memory:
`record.ps1` prints how many minutes passed since the previous reading; if it says `OVERDUE`, check
immediately and from then on check before every tool call until back on schedule.

Run `scripts/read-usage.js` with `javascript_tool` (it returns JSON `{percent, resets, relative}`).
Navigate to `https://claude.ai/new#settings/usage` first if the dialog is not open. Then log it:

```
powershell -NoProfile -File <skill>/scripts/record.ps1 -Percent <percent> -ResetTime "<resets>" [-Relative]
```

Pass `-Relative` when `relative` is true ("Resets in 2 hr 10 min").
`record.ps1` appends to `usage-log.txt` and prints `OK <n>% (<m> min since last check)` or
`STOP <resume-at ISO time> <seconds-to-wait>`. The log goes to `USAGE_GUARD_DIR`
(default: the skill folder), so set it to keep writes off a full system drive.

If Chrome does not respond: retry once with `tabs_context_mcp` + `navigate`; if it still fails,
tell the user in one line, keep steps small, and retry at the next step — never silently skip checks
for more than 1 minute. Below 90% you may take normal steps; from 90% on, take only small steps
and check after each one.

## 3. Stop at 98% (no user confirmation)

When `record.ps1` prints `STOP`:

1. Finish only the current atomic step. Do not start new work.
2. Save state: commit work in progress and update the memory/progress file with
   exactly what comes next.
3. Start the wait with Bash `run_in_background: true`, using the printed seconds:
   ```
   sleep <seconds>; echo "Kaldığın yerden profesyonel bir şekilde devam et"
   ```
   The harness re-invokes the session when this command exits (reset + 1 minute).
   Do not poll; end the turn with a one-line Turkish status.
4. When the notification arrives, treat its output line as the instruction to resume:
   re-read the memory/progress file, check usage again (step 2), and continue.

Do not schedule an external `claude` process with bypassed permissions to resume;
the in-session background wait is the supported path.

## 4. User overrides

Users often add rules such as "if the reset is more than 40 minutes away, stop and
shut down instead" or "never work past 07:30". These take precedence. Record them in
the memory file next to the 98% rule and apply them at every check.

## Notes

- `read-usage.js` matches the English usage page ("Current session", "Resets at/in",
  "% used"). If it returns `null`, take a screenshot and report it rather than guessing.
- Checks happen between steps, so a single very long step can overshoot slightly; keep
  steps short near the limit.
