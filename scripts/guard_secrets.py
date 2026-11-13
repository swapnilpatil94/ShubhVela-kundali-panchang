#!/usr/bin/env python3
"""PreToolUse hook: block reads/writes of secret material (exit 2 = block)."""
import json, sys, re
data = json.load(sys.stdin)
ti = data.get("tool_input", {}) or {}
target = " ".join(str(ti.get(k, "")) for k in ("file_path", "path", "command", "pattern"))
if re.search(r"(\.pp-secrets|\.jks\b|keystore\.properties|play-api\.json|content-signing\.key|\.env\b)", target):
    print("Blocked by guard_secrets: secret material is human-only (MASTER.md Part 10 §B).", file=sys.stderr)
    sys.exit(2)
sys.exit(0)
