# MASTER_PROMPT.md: Copy-paste prompts for building with Fable and Sonnet

## Step 0: one-time setup (you, on the build laptop)
1. Create a folder `parivaar-panchang`, run `git init`, and copy in everything from `build-kit/`:
   - `MASTER.md`, `MASTER_PROMPT.md`, `CLAUDE.md`
   - `.claude/` (agents, commands, settings)
   - `scripts/`
   - `design/` (contact-sheet.html and the prompt cards)
2. Install Android Studio (with an emulator), JDK 21, Python 3, Node LTS, and Claude Code.
3. Start Claude Code in the folder and run `/login` with your Claude plan.
   - For browser help, start it with `claude --chrome`. This needs the Claude in Chrome extension and a direct Claude plan. It uses your logged-in Chrome ([REDACTED_EMAIL_ADDRESS_4]) and pauses at login pages for you.
4. The main session runs **Sonnet** (set in `.claude/settings.json`). Fable and Opus are called automatically through the subagents.

## Prompt 1: kickoff (paste once)
```
You are the orchestrator for Parivaar Panchang. Read CLAUDE.md, then ONLY MASTER.md Part 0 and Part 11 (use line ranges).

1. Check that .claude/agents/{architect,engine-dev,implementer,reviewer,scout}.md, .claude/commands/milestone.md, .claude/settings.json and scripts/guard_secrets.py exist and match MASTER.md Appendix A. Create any that are missing.
2. Create docs/DECISIONS.md, docs/STATUS.md, docs/specs/ and a .gitignore. The .gitignore must cover local.properties, keystore.properties, *.jks, *.keystore, .env, ~/.pp-secrets, build/, .gradle/ and .idea/.
3. Ask the architect subagent to read MASTER.md in full ONCE and write docs/specs/OVERVIEW.md (at most 150 lines) containing:
   (a) contradictions and how it resolves them
   (b) the R1 critical path
   (c) the TODO(owner) checklist, each item with the milestone it blocks: domain, Firebase project, AdMob ad units, keystore, Play service account, Facebook App ID, jyotish writer, native reviewers, pandit fixtures, deity art, audio recordings, legal review
4. Show me only the TODO(owner) checklist, then run /milestone M00.

Hard rules:
- Never type passwords or 2FA codes.
- Never accept terms, add payment details or press any final Create/Submit/Publish/Roll out button. Prepare the steps and wait for me.
- Never read or write ~/.pp-secrets or keystores.
- Debug builds use Google test ad IDs only.
```

## Prompt 2: the build loop (repeat for each milestone)
Run these one per session, in order:

> M00 → M01 → M02 → M03 → M04 → M05 → M06 → M07 → M08 → M09 → M10 → M11 → M12 → M13 → M14 → M15 → M15b → M16 → M17 → M18 → M18b → M18c

```
/clear
/milestone M01
```

**Optional:** add a goal so it keeps going until the acceptance criteria are met:
```
/goal docs/specs/M01.md acceptance criteria all pass and the reviewer returns PASS, or stop after 40 turns
```

**Unattended, with a hard cost cap** (rough cap; adjust as you learn real costs):
```bash
claude -p "/milestone M08" --max-turns 80 --max-budget-usd 6
```

**After each milestone:** read the 5-line summary and `docs/STATUS.md`, and do any TODO(owner) items it lists.

## Prompt 3: account steps (use with `claude --chrome` at M15, M15b, M18b and M18c)
```
Using MASTER.md Part 10 only, prepare the console setup for <AdMob ad units | Firebase project + apps | Cloudflare Pages project + DNS + Email Routing | Play Console app + listings + testing tracks>.

Use my logged-in Chrome only to navigate, read and fill NON-SECRET fields (names, descriptions, ad unit names). Stop before every Create/Save/Accept/Submit/Publish button and before any password, 2FA, payment or tax screen, and tell me exactly what to click.

Afterwards, write the resulting IDs (AdMob app ID, ad unit IDs, Firebase app ID, Pages URL) into gradle.properties or docs/IDS.md. Never write secrets there.
```

## Prompt 4: production readiness check (before closed testing)
```
Have the architect subagent (Fable) audit release readiness against MASTER.md Part 5, Part 7 and Part 10 §C–§E, and the M18 audit.

Output docs/RELEASE_READINESS.md as PASS/FAIL per item:
- vitals budget
- crash-free sessions in internal test
- no ads in sacred zones or the first session
- UMP flows
- test-vs-live ad IDs
- app-ads.txt
- assetlinks
- Data safety answers
- target audience 18+
- privacy policy URL
- listings in en, hi, mr, gu
- 12-tester closed-test plan
- staged rollout plan
- rollback plan

Fix FAIL items through the implementer, then re-audit once.
```

## Prompt 5: a quick look at the UI first (optional, after M00)
```
Build a `prototype` flavor of :app with hard-coded sample data that matches design/contact-sheet.html screens 01–22 (DESIGN rules in MASTER Part 6). Include the 5-tab navigation, light/dark toggle, Bada Text toggle, and Hindi/English.

Use the implementer subagent. Save Roborazzi screenshots to design/screens/. No engine, no ads.
```

## What "production ready" means here
- All R1 milestones (M00–M18c) pass the reviewer.
- M18 audit: no open High items.
- Golden engine tests are green, and the festival fixtures have pandit sign-off.
- Live ads appear only in release builds; consent works in the EEA.
- Crashlytics and Analytics show events with no personal data.
- Vitals in internal and closed testing: crash < 0.3%, ANR < 0.2%.
- Closed test: 12+ testers for 14+ days (personal account created after Nov 2023).
- Production access approved, then a staged rollout of 10%, 50%, 100%.
