"""PreToolUse hook: deny force pushes. Replaces the aitmpl inline version whose
`if` filter was ignored, which made it deny every Bash command."""
import json
import re
import sys

data = json.load(sys.stdin)
command = data.get("tool_input", {}).get("command", "")
if re.search(r"\bgit\s+push\b[^\n;&|]*(\s--force(-with-lease)?\b|\s-[a-zA-Z]*f\b)", command):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": "Force push is blocked by hook",
        }
    }))
sys.exit(0)
