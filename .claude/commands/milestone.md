---
description: Run one milestone end-to-end with model routing (spec → build → review → commit)
argument-hint: <MILESTONE_ID e.g. M03>
---
Run milestone $ARGUMENTS using MASTER.md Part 11 routing:
1. If docs/specs/$ARGUMENTS.md does not exist, invoke the **architect** subagent to write it (it reads only the MASTER parts listed for $ARGUMENTS in Part 12). Do not read those parts yourself.
2. For items marked CRITICAL in the spec, invoke **engine-dev**. For everything else invoke **implementer** (or do it yourself if you are already Sonnet and the change is small).
3. Run the acceptance test tasks from the spec. Fix failures via the same subagent that wrote the code.
4. Invoke **reviewer**. If it reports blocking issues, send them to implementer/engine-dev, then re-run reviewer (max 2 loops; then stop and report).
5. On PASS: update docs/DECISIONS.md and docs/STATUS.md (one line: ID, date, summary, TODO(owner) items), then `git add -A && git commit -m "$ARGUMENTS: <summary>"`.
6. Reply with a 5-line summary only. Never paste file contents.
