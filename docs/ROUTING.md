# ROUTING.md: Model routing and token budget (Claude Code)

**Rule:** deep knowledge comes from Fable, reliable coding from Sonnet, and judgment from Opus. Each one sees only what it needs.

## Roles
These subagents are defined in `.claude/agents/` and listed in full in Appendix A.

| Agent | Model / effort | Does | Never |
|---|---|---|---|
| **Main session** | `sonnet` (Sonnet 5.5), effort medium (`.claude/settings.json`) | Orchestrates `/milestone`. Does small edits itself | Reads whole MASTER.md or large files |
| **architect** | `fable` (Fable 5.2), high | Writes `docs/specs/<ID>.md`: a 1-page spec with exact rules, APIs, tests and acceptance criteria | Writes production code |
| **engine-dev** | `fable`, high | Writes CRITICAL code: engine math, crypto, festival rule evaluator | UI and plumbing |
| **implementer** | `sonnet`, medium | Everything else: UI, data, ads, billing, analytics, scripts, tests | Changes specs or tolerances |
| **reviewer** | `opus` (Opus 5.5), high | Reviews the diff against the spec, checking security, policy and accessibility, and returns PASS or a list of issues | Rewrites code |
| **scout** | `haiku`, low | Finds files, symbols and MASTER sections | Edits |
| Translation (script, not an agent) | Haiku 5.5 via the Batch API (−50% cost) | Bulk content packs | — |

## Why this saves tokens without losing accuracy
- **The spec is written once and stored** (`docs/specs/<ID>.md`). The cheaper model then implements from about 120 lines, not from Fable's whole context.
- **Correctness is pinned by tests, not by model choice.** Golden fixtures come from Skyfield + DE440, so Sonnet can't be "confidently wrong" without CI failing.
- **The reviewer reads only the diff** plus the cited MASTER parts.
- **Every agent has a turn cap** (`maxTurns`). Use `/goal` with a turn clause, and `--max-budget-usd` for unattended runs.
- **Run `/clear` between milestones**, and keep `CLAUDE.md` short.

## How to run
**Interactive (recommended):**
```
/milestone M03
```
Optionally put a goal on top of it:
```
/goal M03 acceptance criteria in docs/specs/M03.md all pass and reviewer returns PASS, or stop after 40 turns
```

**Unattended (hard cost cap):**
```bash
claude -p "/milestone M08" --max-turns 80 --max-budget-usd 6
```

**Switch the main model by hand only when needed:** `/model fable` for a hard design discussion, then `/model sonnet` again.

## Milestone routing table
Spec = architect (Fable) · CRIT = engine-dev (Fable) · Build = implementer (Sonnet) · Review = reviewer (Opus).

| ID | Spec | CRIT | Build | Review | Notes |
|---|---|---|---|---|---|
| M00 Bootstrap | — | — | ✓ | — | CI green is the gate |
| M01 Astronomy core | ✓ | ✓ (backends, time, rise/set) | oracle scripts, wiring | ✓ | Golden tests |
| M02 Jyotish core | ✓ | ✓ (ayanamsa, houses, dashas) | vargas tables, yoga DSL loader | ✓ | |
| M03 Panchang | ✓ | ✓ (limb solver, windows) | tables, API surface | ✓ | |
| M04 Calendar & festivals | ✓ (effort **xhigh**) | ✓ (rule evaluator, Ekadashi, solar rules) | fixtures loader, JSON rules | ✓ | Pandit fixtures are TODO(owner) |
| M05 Geo & tz | ✓ | — | ✓ | ✓ | |
| M06 Secure data | ✓ | ✓ (key mgmt, snapshot crypto) | repositories | ✓ | |
| M07 Design system & shell | ✓ (from contact sheet) | — | ✓ | ✓ (screenshots) | |
| M08 Aaj, widgets, tile, notifications, travel mode, read-aloud | ✓ | — | ✓ | ✓ | |
| M09 Calendar UI, regional views, ICS, "Should I…?" | ✓ ("Should I…?" rules) | — | ✓ | ✓ | |
| M10 Kundli & family | — | — | ✓ | ✓ | Engine already done |
| M11 Content packs & rashifal | ✓ | ✓ (signature verify) | ✓ | ✓ | |
| M12 Bonds & sharing | ✓ | ✓ (Bond Code codec) | ✓ | ✓ | |
| M13 Bhakti & Sankalp | — | — | ✓ | ✓ | |
| M14 PDF reports | ✓ | — | ✓ | ✓ | |
| M15 Ads (AdMob), UMP, billing | ✓ (policy) | — | ✓ | ✓ + **architect policy pass** | Test ad IDs in debug |
| M15b Observability & analytics (OPS §D) | — | — | ✓ | ✓ (privacy) | |
| M16 Localization pipeline | — | — | ✓ | — | Haiku Batch script |
| M17 Performance & a11y | — | — | ✓ | ✓ | |
| M18 Security audit (MASVS) | — | — | fixes | **architect** (Fable) audit | |
| M18b Cloudflare site & infra (OPS §A, §C) | — | — | ✓ | ✓ | Privacy policy, terms, app-ads.txt, assetlinks, content packs on Pages |
| M18c Play release pipeline (OPS §E) | — | — | ✓ | ✓ | The human presses every final button |

## Token hygiene checklist (in CLAUDE.md)
- Use `scout` for lookups.
- Never `cat` big files. Read line ranges.
- Never paste file contents into chat replies.
- Run the narrowest Gradle task first.
- Commit after every milestone, then `/clear`.
