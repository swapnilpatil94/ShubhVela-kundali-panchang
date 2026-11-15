# Should I...? owner fixture template (TODO(owner))

v1 (decision D8) gives all 30 intents the same rule: avoid = Rahu Kaal, Yamaganda, Gulika, Durmuhurta, Varjyam,
Bhadra; prefer = Amrit, Shubh, Labh choghadiya plus Abhijit; no tithi, weekday or nakshatra checks; every entry is
`status: VERIFY`, `sourceId: pp-v1-header-rule`. No per-intent traditional rule is invented.

Fill one row per intent. Leave a cell empty for "no check". Every row needs a source id from the source list.
Do not add prose, warnings or remedies; ids and numbers only.

| intent id | avoid window kinds | prefer choghadiya codes | tithiAvoid (1..30) | vaarPrefer (SUNDAY..SATURDAY) | nakshatraPrefer (0..26) | sourceId | reviewer | date |
|---|---|---|---|---|---|---|---|---|
| vehicle_purchase | | | | | | | | |
| vehicle_delivery | | | | | | | | |
| property_purchase | | | | | | | | |
| property_registration | | | | | | | | |
| griha_pravesh | | | | | | | | |
| construction_start | | | | | | | | |
| new_job_join | | | | | | | | |
| job_interview | | | | | | | | |
| business_start | | | | | | | | |
| shop_opening | | | | | | | | |
| contract_signing | | | | | | | | |
| investment | | | | | | | | |
| gold_purchase | | | | | | | | |
| electronics_purchase | | | | | | | | |
| clothes_purchase | | | | | | | | |
| travel_start | | | | | | | | |
| pilgrimage_start | | | | | | | | |
| haircut | | | | | | | | |
| mundan | | | | | | | | |
| naming_ceremony | | | | | | | | |
| vidyarambha | | | | | | | | |
| exam_start | | | | | | | | |
| new_course | | | | | | | | |
| engagement_talk | | | | | | | | |
| gift_giving | | | | | | | | |
| puja_start | | | | | | | | |
| vrat_start | | | | | | | | |
| planting | | | | | | | | |
| moving_house | | | | | | | | |
| bank_account_open | | | | | | | | |

Also confirm: the 30-intent list, the answer copy (Good time now / Better later today / Tomorrow from), and the
explainer ids (`why_tithi`, `why_vaar`, `why_nakshatra`, `why_abhijit`, `why_choghadiya_<code>`, `why_avoid_<kind>`).
