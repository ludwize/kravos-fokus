# Kravos Fokus

Personal, sideloaded digital-wellbeing app (ScreenZen / Opal / AppBlock / StayFree style), no ads, nothing leaves the phone.

- App groups with daily time and opens limits, schedules, soft or strict mode (strict edits apply next day)
- Shorts / Reels / Spotlight blocking, TikTok whole-app block
- Focus sessions (optionally strict)
- Stats from UsageStatsManager: per app, hourly, unlocks, opens, categories, weekly insights, goal streak
- Evening report notification, limit warnings, optional uninstall protection during strict blocks

Built only in GitHub Actions (`.github/workflows/build.yml`), signed with `signing/release.jks.enc` + secret `KP_SIGNING_PASS`, self-updates from Releases.
