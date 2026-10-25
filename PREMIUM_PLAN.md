# SatraNG premium plan (branch `satra-premium`)

## Design system — `ui/compose/satra/`
- **Brand**: orange `#FF6A00` (accent), warm-neutral surfaces. Dark = near-black `#0B0B0D` with raised cards `#17171A`/`#1F1F23`; Light = grouped-inset grey `#F2F2F7` with white cards (Apple "grouped" look). Status colours: connected green, connecting amber, error red.
- **No glass / blur / liquid**: depth comes from solid layered surfaces, hairline separators and soft ambient shadows. Module `liquidglass-*` + `third_party/Abdullajon-LiquidGlass` removed.
- **Tokens** (`SatraTheme`): colour roles, shapes (continuous-style rounded 12/16/22/28), spacing 4-pt grid, type scale (large title 34, title 22, headline 17, body 15, caption 12; Vazirmatn for `fa`).
- **Motion** (`SatraMotion`): springs (`press` 0.6/700, `soft` 0.82/380, `bouncy` 0.55/260), stagger entrance, `reduce motion` honoured (animator scale 0), haptics via `SatraHaptics` (tick, confirm, reject).
- **Components**: large-title scaffold, inset grouped lists, press-scale buttons, switch, segmented control, bottom tab bar (solid), bottom sheets, context menus, ConnectOrb, PingPill, charts (bar/sparkline/ring).
- Icons: hand-authored SF-style stroke icon set (`SatraIcons`).

## Screens (single `MainActivity`, 5 tabs)
Home (connect orb, live speed graph, timer, exit IP flag, ping pill, quick servers) · Servers (search, group chips, sort by ping, favourites, swipe actions, undo delete, bulk ping, QR/share) · Subscriptions (cards, quota ring, expiry, add/edit sheet, auto-update, QR/paste) · Usage (7/30 day charts, hours connected, per-app, limits, domain rules) · Settings (grouped inset, search, advanced behind toggle; legacy `SettingsActivity` restyled as "Advanced").

## Usage architecture
- Totals: Xray outbound stats (exact, through tunnel) → `UsageLedger` (day buckets, connected-seconds, session log).
- Per-app breakdown: `NetworkStatsManager` per-UID (needs *Usage access*; approximate = all traffic of app while VPN up, incl. bypassed).
- **Limits** (`UsageLimitEngine`): per-app (process/UID routing) and per-domain rules. Core config gets a *counting outbound clone* per rule (`lim-<id>`) so Xray stats give exact bytes; when quota is exhausted the rule flips to `block` and the core is reloaded in place (VPN stays up). Periods daily/weekly/monthly reset at local midnight. App rules need Android 10+ and Xray-TUN engine (auto-switched with consent); domain rules work with both engines.
- Exit-IP geo: `ExitIpProbe` starts an isolated temp core for the config (in `:tasks`), asks the IP API through it, caches `(cc, ip, ts)`; fallback = geolocate resolved server IP; TTL 24h; never blocks UI.
