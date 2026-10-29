# Changelog

## v4.1.1 — 2026-10-29

- Add QQ direct-routing defaults to exported Mihomo configurations: qq.com, gtimg.com, qpic.cn and QQ.exe.
- Keep the final MATCH,PROXY fallback after the direct rules and export rule mode.
- Preserve existing speed-prefixed names and keep internal speed-test routing unchanged.
- Add export routing regression checks and validate the generated configuration with Mihomo.

## v4.1.0 — 2026-10-29

- Generate modern-node speed-test configuration directly for Mihomo.
- Use isolated proxy listeners for per-node downloads and respect configured concurrency.
- Support Reality empty short IDs and safely serialize configuration values.
- Encode delay API URLs, bypass the system proxy for local control calls and increase configuration reload timeout.
- Calculate peak download speed using the actual sample duration.
- Prefix exported names with valid average speeds across share URLs, Base64 subscriptions and Mihomo configurations.
- Preserve source names and handle duplicate exported names.
- Replace the default download test destination with an HTTPS endpoint.
- Add clean release packaging, pinned runtime downloads and export regression checks.

Based on the MIT-licensed nodesCatchNext source by AHackerX, derived from nodesCatch V2.0 by 不良林.
