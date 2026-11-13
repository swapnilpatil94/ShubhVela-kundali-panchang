# MONETIZATION.md: Ads, rewarded unlocks, Plan

## Principles
1. Everything is usable for free. Ads pay for convenience (PDF exports, deep-dives, extra themes), never for access.
2. The Plan removes ads and adds cosmetic perks. It never unlocks calculations.
3. Trust comes before eCPM. Respect the sacred zones (PRODUCT.md), show no full-screen ad in the first session, and never interrupt timing screens.

## Ad stack
- Google Mobile Ads (use the GMA Next-Gen SDK if it is stable at build time; otherwise the current GMA SDK) with AppLovin MAX or AdMob mediation. Bidders: InMobi, Meta Audience Network, Pangle, Unity, Liftoff. Verify each one's SDK Index status first.
- UMP consent: EEA/UK/CH messages and the US-states message. India needs no CMP, but the privacy notice must explain ads.
- `maxAdContentRating = PG`.
- Age-restricted treatment: if the user's own profile shows age < 18, set the `setAgeRestrictedTreatment` value for teen/child, request non-personalized ads only, and turn analytics collection off.
- **Blocked categories:** Gambling & Betting, Alcohol, Dating, Get-rich-quick, Sexual reference, Sensationalism, Social casino.
- **Allowed:** Religion.
- AdMob removed the "Astrology & esoteric" category on 23 Oct 2026. Block known fear-selling astrology and remedy advertisers by URL, and review the Ad review center weekly.
- Server-side verification (SSV) for any reward that has a cost on our side (AI questions). If an ad fails to load or fill after one retry, grant the reward anyway.

## Placements (cap every format at app level *and* per unit)
| Format | Where | Caps |
|---|---|---|
| Native (Compose) | Below the fold on Aaj, Calendar month, Rashifal, Kundli overview | 1 per screen |
| Adaptive banner | Calendar, tools, rashifal list | Never on sacred zones |
| Interstitial | Only at natural breaks: after a match result, after a kundli is generated, when leaving a report | ≥ 3 min apart, ≤ 3 per day, none on day 1 |
| Rewarded | User taps "Watch ad to unlock" | ≤ 6 per day |
| Rewarded interstitial | After the reveal of a kundli or bond, an intro screen offers a bonus deep-dive | Must have an intro screen and a skip; ≤ 2 per day |
| App-open | Cold start from the 3rd session on, only if the last ad was > 4 h ago | ≤ 1 per day; never when launched from a notification or widget |

## Rewarded unlock table
| Unlock | Grant |
|---|---|
| PDF export of any report | 1 export |
| Bond deep-dive / Squad | Unlimited for that bond, 7 days |
| Personalised muhurat filter | 1 search session |
| Card theme | 24 h |
| Yearly PDF calendar | 1 export |
| AI question (R3) | 1 question (max 3/day) |
| "Calm mode" (no interstitials) | 3 h, once per day |

Every rewarded button shows the alternative next to it, for example "or Go ad-free ₹49".

## Plan ("Parivaar Plus")
**Perks:**
- No ads anywhere.
- All rewarded unlocks always on.
- Every card theme.
- 10 AI questions a day (R3).
- Supporter badge on cards.

**Purchase options:**
- Subscriptions: monthly and yearly base plans.
- Prepaid passes for India: 1 month and 12 months, no autopay.
- A one-time Lifetime purchase.
- Use Play Billing Library 8 or later. Pricing templates no longer exist, so set prices per product and per country through the Play Developer API `monetization` endpoints, using a checked-in JSON file (`play/prices.json`).

**Prices:** these are starting points. Recalibrate after 2 weeks of AdMob country data. Rule: yearly price ≥ 2× the ad revenue of a heavy user per year.

| Market | Monthly | Yearly | Lifetime |
|---|---|---|---|
| India | ₹49 (prepaid ₹59) | ₹399 (prepaid ₹449) | ₹999 |
| Nepal / Bangladesh / Sri Lanka | ≈$0.99 | ≈$4.99 | ≈$11.99 |
| US | $1.99 | $11.99 | $29.99 |
| Canada | CA$2.49 | CA$14.99 | CA$34.99 |
| UK | £1.49 | £9.99 | £24.99 |
| Eurozone | €1.99 | €11.99 | €27.99 |
| Australia / NZ | A$2.99 | A$17.99 | A$39.99 |
| UAE / Gulf | AED 6.99 | AED 44.99 | AED 109.99 |
| Singapore | S$2.98 | S$15.98 | S$38.98 |
| Malaysia | RM4.90 | RM29.90 | RM69.90 |
| Mauritius, Fiji, Caribbean, Africa, LatAm | USD template ×0.6 | | |

**Offers:**
- 7-day trial on the yearly plan, tier-1 markets only.
- Festival offer (Navratri to Diwali): 30% off yearly.
- Win-back offer for lapsed subscribers.
- Referral: when a friend installs through your link and stays active for 3 days, you get 7 ad-free days. Attribution uses the Play Install Referrer API. Never reward reviews or ratings.

**Fees:** India is still on the legacy Play schedule (15% on subscriptions and on the first $1M). The US, UK, EEA, AU and JP moved to 10% + 5% billing in 2026. Assume Rest of World follows on 30 Sep 2027.

## Audience-specific tuning (via Remote Config segments; no personal data)
| Segment (derived on device) | Tuning |
|---|---|
| Ritual keepers (heavy Aaj/Calendar use) | Native ads only; no interstitials before day 7 |
| Marriage matching (F1/E7 users) | Interstitial after the match result is OK; push the matching-report PDF as a rewarded unlock |
| Gen-Z (Bonds users) | Rewarded ads for deep-dives and themes; app-open off |
| Diaspora (locale outside IN) | Same caps (higher eCPM does the work); show the local Plan price |
| Power users (D4/D6/D7) | Banner only; promote the Plan after 10 sessions |
| Under 18 (from own profile) | Non-personalized ads only, no analytics, Bonds sharing limited to name initials |

## KPIs to log (aggregate only)
- ARPDAU by country.
- Rewarded opt-in as a share of DAU.
- Interstitials per DAU.
- D1/D7/D30 retention split by ad exposure: keep a 5% holdout with no interstitials.
- Plan conversion.
- Refund rate.
