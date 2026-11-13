#!/usr/bin/env python3
"""
Unit-economics + opportunity-scoring model for the astrology/panchang research report.
All inputs are ASSUMPTIONS unless marked; sources are listed in the report.
Edit the dictionaries below and re-run:  python3 model.py > model_output.md
"""

FX = 88.0          # INR per USD (assumption)
DAYS = 30.4        # days per month

# ---------------------------------------------------------------- scenarios
S = {
    "Conservative": dict(
        arpdau_in=0.0007,      # USD, India ad ARPDAU (research: ESTIMATED, impressions x eCPM x fill)
        diaspora_share=0.03,   # share of DAU outside India (US/UK/CA/UAE/AU)
        diaspora_mult=6,       # diaspora ARPDAU multiple vs India (US eCPMs ~10x India; fewer sessions)
        dau_mau=0.18,
        sub_conv=0.003,        # share of MAU on paid ad-free family plan
        ai_users=0.05,         # share of DAU using cloud AI per day (stress: high usage, low revenue)
        ai_convs=2, ai_cost=0.002,  # conversations per AI user/day, USD per conversation (Indic tokens x2-4)
    ),
    "Base": dict(
        arpdau_in=0.0027, diaspora_share=0.08, diaspora_mult=8, dau_mau=0.25,
        sub_conv=0.008, ai_users=0.03, ai_convs=2, ai_cost=0.001,
    ),
    "Optimistic": dict(
        arpdau_in=0.0094, diaspora_share=0.12, diaspora_mult=10, dau_mau=0.33,
        sub_conv=0.015, ai_users=0.02, ai_convs=2, ai_cost=0.0005,
    ),
}

SUB_PRICE_IN_YR = 249      # INR/yr India ad-free family plan (assumption; Drik lists ~Rs199/yr on iOS)
SUB_PRICE_DIA_YR = 9.99    # USD/yr diaspora
PLAY_FEE = 0.15            # Play service fee on first $1M/yr and on subscriptions (VERIFIED, India legacy schedule)

FIXED_NONTEAM_USD = 200    # domains, Cloudflare $5, tools, amortised SwissEph CHF700/6yr, legal review reserve
TEAM_OPTIONS_INR = {"Founders unpaid": 0, "Lean (2 eng + part-time content)": 300_000, "Small team (5-6)": 1_000_000}

DAU_LEVELS = [1_000, 10_000, 100_000, 1_000_000]


def remote_config_cost(dau):
    fetches = dau * 1.0                       # 1 fetch/DAU/day with 12h min interval
    over = max(0, fetches - 100_000) * DAYS   # 100k/day free (VERIFIED Firebase, from 1 Sep 2026)
    return over / 10_000 * 0.06


def workers_cost(dau, mult=1.0):
    req = dau * 3 * DAYS                      # 3 edge requests/DAU/day (content packs, share cards, AI proxy)
    if req <= 100_000 * DAYS:
        return 0.0
    return mult * (5 + max(0, req - 10_000_000) / 1e6 * 0.30)


def support_cost(mau):
    return mau * 0.0007                      # ~1 support/content-ops person (Rs30k) per ~500k MAU


def month(dau, p, team_inr=0, ai_mult=1.0, ad_mult=1.0, cloud_mult=1.0, no_rewarded=False):
    mau = dau / p["dau_mau"]
    arpdau_in = p["arpdau_in"] * ad_mult * (0.88 if no_rewarded else 1.0)  # rewarded ~12% of base ARPDAU
    blended = arpdau_in * (1 - p["diaspora_share"] + p["diaspora_share"] * p["diaspora_mult"])
    ads = dau * blended * DAYS
    subs_in = mau * (1 - p["diaspora_share"]) * p["sub_conv"] * SUB_PRICE_IN_YR / 12 / FX
    subs_dia = mau * p["diaspora_share"] * p["sub_conv"] * 2 * SUB_PRICE_DIA_YR / 12
    subs = (subs_in + subs_dia) * (1 - PLAY_FEE)
    ai = dau * p["ai_users"] * p["ai_convs"] * p["ai_cost"] * DAYS * ai_mult * cloud_mult
    infra = (remote_config_cost(dau) + workers_cost(dau, cloud_mult))
    sup = support_cost(mau)
    var = ai + infra + sup
    fixed = FIXED_NONTEAM_USD + team_inr / FX
    return dict(mau=mau, ads=ads, subs=subs, ai=ai, infra=infra, sup=sup,
                contrib=ads + subs - var, net=ads + subs - var - fixed, fixed=fixed,
                blended=blended)


def fmt(x):
    return f"${x:,.0f}"


def inr(x):
    v = x * FX
    if abs(v) >= 1e7:
        return f"₹{v/1e7:,.2f} Cr"
    if abs(v) >= 1e5:
        return f"₹{v/1e5:,.1f} L"
    if abs(v) < 100:
        return f"₹{v:,.1f}"
    return f"₹{v:,.0f}"


def main():
    print("## F1. Monthly P&L by scale (ads + optional ad-free plan), USD\n")
    for name, p in S.items():
        print(f"### {name}  (India ARPDAU ${p['arpdau_in']}, diaspora {p['diaspora_share']:.0%} of DAU at {p['diaspora_mult']}x, DAU/MAU {p['dau_mau']})\n")
        print("| DAU | MAU | Ad revenue | Ad-free plan (net) | AI cost | Infra | Support | Contribution / mo | Contribution / yr (₹) |")
        print("|---|---|---|---|---|---|---|---|---|")
        for d in DAU_LEVELS:
            m = month(d, p)
            print(f"| {d:,} | {m['mau']:,.0f} | {fmt(m['ads'])} | {fmt(m['subs'])} | {fmt(m['ai'])} | {fmt(m['infra'])} | {fmt(m['sup'])} | {fmt(m['contrib'])} | {inr(m['contrib']*12)} |")
        print()

    print("## F2. Break-even DAU by team cost (contribution must cover non-team fixed $200 + team)\n")
    print("| Scenario | " + " | ".join(TEAM_OPTIONS_INR) + " |")
    print("|---|" + "---|" * len(TEAM_OPTIONS_INR))
    for name, p in S.items():
        row = []
        for team in TEAM_OPTIONS_INR.values():
            lo, hi = 100, 50_000_000
            if month(hi, p, team)["net"] < 0:
                row.append("not reached")
                continue
            while hi - lo > 50:
                mid = (lo + hi) // 2
                if month(mid, p, team)["net"] >= 0:
                    hi = mid
                else:
                    lo = mid
            row.append(f"~{hi:,}")
        print(f"| {name} | " + " | ".join(row) + " |")
    print()

    print("## F3. Stress tests at 100,000 DAU, Base scenario, lean team (₹3L/mo)\n")
    p = S["Base"]
    team = 300_000
    cases = [
        ("Base", {}),
        ("Ad revenue −50%", dict(ad_mult=0.5)),
        ("AI usage 3x", dict(ai_mult=3)),
        ("Cloud costs 2x", dict(cloud_mult=2)),
        ("Users refuse rewarded ads", dict(no_rewarded=True)),
    ]
    print("| Case | Revenue | Variable cost | Net after team | Net (₹/mo) |")
    print("|---|---|---|---|---|")
    for label, kw in cases:
        m = month(100_000, p, team, **kw)
        print(f"| {label} | {fmt(m['ads']+m['subs'])} | {fmt(m['ai']+m['infra']+m['sup'])} | {fmt(m['net'])} | {inr(m['net'])} |")
    # weaker retention: same installs, lower DAU -> model as 40% fewer DAU
    m = month(60_000, p, team)
    print(f"| Retention weaker (DAU 100k→60k for same installs) | {fmt(m['ads']+m['subs'])} | {fmt(m['ai']+m['infra']+m['sup'])} | {fmt(m['net'])} | {inr(m['net'])} |")
    q = dict(p, diaspora_share=0.0)
    m = month(100_000, q, team)
    print(f"| No diaspora users | {fmt(m['ads']+m['subs'])} | {fmt(m['ai']+m['infra']+m['sup'])} | {fmt(m['net'])} | {inr(m['net'])} |")
    m = month(100_000, p, team, ad_mult=0.5, ai_mult=3, cloud_mult=2, no_rewarded=True)
    print(f"| **All adverse at once** | {fmt(m['ads']+m['subs'])} | {fmt(m['ai']+m['infra']+m['sup'])} | {fmt(m['net'])} | {inr(m['net'])} |")
    print()

    print("## F4. Install-level payback (can ads fund paid acquisition?)\n")
    print("| Scenario | Active days per install (180d) | Ad LTV / install | Max CPI at 100% payback | Typical India CPI (₹22–60) |")
    print("|---|---|---|---|---|")
    for name, p, days in [("Conservative", S["Conservative"], 5), ("Base", S["Base"], 10), ("Optimistic", S["Optimistic"], 20)]:
        ltv = days * month(1, p)["blended"]
        print(f"| {name} | {days} | ${ltv:.3f} ({inr(ltv)}) | {inr(ltv)} | ₹22–60 → {'NO' if ltv*FX < 22 else 'marginal'} |")
    print()

    print("## F5. Rewarded-ad funding of AI conversations\n")
    print("| Rewarded eCPM (India) | Revenue per completed view | Cost per AI conversation | Conversations funded per view |")
    print("|---|---|---|---|")
    for ecpm in (0.8, 1.5, 3.0):
        for c in (0.0004, 0.001, 0.002):
            print(f"| ${ecpm:.2f} | ${ecpm/1000:.4f} | ${c:.4f} | {ecpm/1000/c:.1f} |")
    print()

    # ---------------------------------------------------------- opportunity scoring
    crit = ["Demand", "Low competition", "Distribution", "Differentiation", "Low build cost", "Low run cost",
            "Monetization", "Retention", "Defensibility", "Trust/privacy", "Speed to MVP", "Scalability"]
    w = [1.5, 1.0, 1.25, 1.0, 0.75, 1.0, 1.25, 1.5, 0.75, 0.75, 0.75, 1.0]
    concepts = {
        "1. Family ritual calendar (panchang + vrat + family tithi dates, regional + diaspora-correct)": [5, 3, 4, 4, 4, 5, 3, 5, 3, 5, 4, 4],
        "2. Diaspora-first panchang (NRI timings, festivals by local date)":                              [3, 3, 3, 4, 4, 5, 4, 4, 3, 5, 4, 3],
        "3. Single regional-language calendar (e.g., Telugu/Kannada/Gujarati)":                            [4, 3, 4, 3, 4, 5, 3, 5, 3, 4, 4, 2],
        "4. Free kundli + daily horoscope (ad-funded AstroSage alternative)":                              [5, 1, 2, 2, 4, 5, 3, 3, 1, 4, 4, 4],
        "5. Free AI astrologer chat (rewarded-ad funded)":                                                  [4, 1, 2, 2, 3, 2, 2, 3, 1, 2, 3, 3],
        "6. Fair-priced human consultation marketplace":                                                    [5, 1, 1, 3, 2, 2, 5, 3, 2, 2, 2, 4],
        "7. Transparent kundli matching for families + matrimony B2B API":                                  [4, 2, 3, 3, 4, 5, 3, 1, 2, 4, 4, 3],
        "8. Muhurat planner (wedding, griha pravesh, vehicle, naming)":                                     [4, 3, 3, 3, 3, 5, 3, 2, 2, 4, 3, 3],
        "9. Pro chart tool for serious learners (mobile JHora)":                                            [2, 4, 2, 4, 2, 5, 2, 4, 3, 5, 2, 2],
        "10. Astrology learning app (courses, guided chart reading)":                                       [2, 3, 2, 3, 2, 4, 3, 2, 2, 4, 2, 3],
        "11. Gen-Z social Vedic astrology (friends, compatibility, shareables)":                            [3, 2, 4, 3, 3, 4, 2, 2, 2, 3, 3, 3],
        "12. WhatsApp-first panchang/rashifal channel + bot":                                               [4, 3, 5, 2, 5, 4, 1, 3, 1, 4, 5, 3],
        "13. B2B panchang/astro API & white-label (temples, matrimony, publishers)":                        [2, 3, 2, 3, 3, 4, 4, 4, 3, 4, 3, 3],
        "14. Devotional + puja services marketplace":                                                       [4, 2, 2, 2, 2, 2, 5, 3, 2, 2, 2, 4],
    }
    print("## D1. Opportunity scores (1–5 ordinal; higher is better on every column)\n")
    print("| Concept | " + " | ".join(crit) + " | Weighted (max 5) |")
    print("|---|" + "---|" * (len(crit) + 1))
    rows = []
    for k, v in concepts.items():
        score = sum(a * b for a, b in zip(v, w)) / sum(w)
        rows.append((score, k, v))
    for score, k, v in sorted(rows, reverse=True):
        print(f"| {k} | " + " | ".join(map(str, v)) + f" | **{score:.1f}** |")
    print("\nWeights: " + ", ".join(f"{c} {x}" for c, x in zip(crit, w)))


if __name__ == "__main__":
    main()
