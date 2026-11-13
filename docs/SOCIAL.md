# SOCIAL.md: Bonds, share cards, viral loops

## Bond Code (no server, no birth data)
A Bond only needs **derived traits**, never DOB, time or place.

```
BondTraitsV1 {
  v:1,
  n:  first name or initials (max 12 chars, user-editable),
  mn: Moon nakshatra 0..26,
  mp: pada 1..4,
  mr: Moon rashi 0..11,
  lg: lagna 0..11 (optional; omitted if birth time unknown),
  mars: Manglik flag 0/1/2 (none/partial/full),
  ven: Venus sign 0..11,
  sun: Sun sign 0..11,
  g:  gana, y: yoni, nd: nadi   (derivable but cached)
}
```
- **Encoding.** Use CBOR or a compact binary layout, then base64url, with a CRC-8 checksum. The result is about 20 characters.
- **Transport.** The code travels as a QR code or as a link: `https://<domain>/b#<code>`.
- **Why the `#`.** Browsers never send the fragment to the server, so the server never sees the code.
- **Opening the link.**
  - With the app installed, an App Link with `autoVerify` opens the Bond screen directly.
  - Without the app, a static web page computes the bond in JS (or shows a teaser) and links to Play. The install referrer carries the code only as `ref=<random id>`, never the traits.
- **Privacy.**
  - These traits cannot practically be reversed into an exact birth date or time. Sun sign plus Moon pada narrows it to a few days in some year, and the year itself is unknown.
  - Lagna is the most revealing trait (it narrows birth time to about 2 hours), so the share screen lets the user leave it out.
  - Show the user exactly what is shared before every share.
- **Data handling.**
  - A friend's Bond is stored on the device as traits only.
  - The user can delete it; the app has no "remove me from their phone" feature, and the UI says so.
- **Manual profiles.** A profile the user typed in for someone else is **never** shareable and never appears on cards.

## Bond scoring
- **Base score.** Ashtakoota (Varna 1, Vashya 2, Tara 3, Yoni 4, Graha Maitri 5, Gana 6, Bhakoot 7, Nadi 8), total out of 36.
- **Axes.** Each bond type re-maps the kootas onto 5 axes (Vibe, Talk, Trust, Fights, Growth). The weights live in `content/bond_weights.json`, so they are versioned content.
- **Friend bonds.** Bestie and Sibling bonds ignore Nadi and Bhakoot dosha because they are marriage-specific, and the app says so.
- **Display.** Show the score as a percent with a word label: Cosmic Twins, Soul Squad, Spicy Combo, Growth Bond, Opposites Attract.
- **No negative labels, ever.** The lowest band is "Growth Bond".

## Share cards (rendered on device)
- **Sizes.** Story 1080×1920 and square 1080×1080. Render with Compose's `GraphicsLayer.toImageBitmap()`, or draw directly on a Canvas.
- **Branding and privacy.** Every card carries a small wordmark, a short link and an "Installed? tap to match" hint. Strip all EXIF data, and never put birth details on a card.
- **Card types.**
  - Today's panchang.
  - Festival greeting with the user's name.
  - Bond result.
  - Nakshatra personality.
  - Yoni animal and Gana.
  - Jap streak.
  - Gita shloka.
  - Cosmic Wrapped, a set of 6 cards.
- **Sharing targets.**
  - **WhatsApp.** `ACTION_SEND` with an image and `setPackage("com.whatsapp")`, falling back to `com.whatsapp.w4b`, then to the system chooser.
  - **WhatsApp Status.** There is no documented intent, so use the chooser.
  - **Instagram Stories.** Use the `com.instagram.share.ADD_TO_STORY` intent with a FileProvider URI. It needs `source_application` set to the Facebook App ID, `<queries>` for `com.instagram.android`, and read permission granted on the URI. Re-check Meta's current docs at build time.
  - **Snapchat.** Optional, via Creative Kit (needs a client ID).
- **Links.** Firebase Dynamic Links is dead (shut down 25 Aug 2025). Use Android App Links plus the Play Install Referrer.

## Loops
1. **Daily panchang card.** The user forwards it to family groups, recipients install, and they set their city.
2. **Bond invite.** A friend enters their details on their own phone, both get a result, and they share their cards.
3. **Festival greetings.** The card pack is pre-loaded 7 days before each festival.
4. **Family dates.** "Remind my siblings" shares the date card (it contains no birth data).
5. **Cosmic Wrapped (December 1–31).** Six story cards.
6. **Referral.** 7 ad-free days, granted after the invited friend has been active for 3 days.

## Notification tone
Notifications are warm, short and never alarming. Examples:
- "Ekadashi tomorrow 🌙 Parana window 6:12–8:40 AM in Pune"
- "Riya's janma nakshatra birthday is Friday 🎂"

Never send dosha alerts or "bad day" pushes.
