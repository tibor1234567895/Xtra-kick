# Kick Migration Report

## Surface Mapping

| Legacy Surface | Kick Replacement | Notes |
| -------------- | ---------------- | ----- |
| Legacy GraphQL `streams`, `games`, `users` | `KickApiClient` using `/livestreams`, `/categories`, `/channels`, `/users` | Typed Kotlin models mirror the Swagger schema; pagination helpers still need to be threaded into the UI data sources. |
| Legacy OAuth (implicit & device code helpers) | `KickOAuthClient` + WebView-based Kick login | Authorization-code + PKCE flow now powers `LoginActivity`; background refresh scheduling remains outstanding. |
| Legacy WebView login handling implicit tokens | `LoginActivity` loading Kick OAuth authorize URL | Exchanges auth code for tokens, persists via `KickTokenStore`, and exposes retry via external browser fallback. |
| Legacy chat IRC over WebSockets | `KickChatClient` targeting `wss://ws.kick.com/v2` with Phoenix join envelopes | Message parsing/backfill for emojis, badges, and replies still required. |
| BetterTTV global/channel emotes wired through `PlayerRepository` | `KickBttvService` retaining BetterTTV loading for Kick chat | Supports global sets today and tries Kick identifiers for channel lookups so third-party reactions stay available. |
| Legacy token persistence in shared prefs | `KickTokenStore` scoped to Kick credentials | Encrypted storage to be evaluated; follow-up needed to migrate existing data safely. |
| Legacy config constants spread across helpers | `KickEnvironment` single configuration surface | Remaining modules must consume this to remove hard-coded hosts. |

## Removed Legacy Artifacts

- GraphQL/Cronet hints pointing at legacy hosts inside `XtraModule`.
- README copy, download links, and branding tied to the previous service.
- Legacy login workflow and manual token entry paths.
- Build configuration fields now derive Kick endpoints and credentials rather than inherited tokens.

Additional legacy-specific models and API wrappers remain and will be excised as the UI migrates to the new Kick repositories.

## Known Gaps & Backlog

1. **UI Integration** – fragment/view-model layers still call the legacy repositories. They must be updated to use `KickApiClient`, `KickOAuthClient`, and `KickChatClient`.
2. **Playback** – the media player currently depends on historical playback token workflows; Kick stream playback endpoints need to be reverse engineered or implemented using documented APIs.
3. **Chat Rich Features** – emoji, badge, and reply parsing still rely on schemas from the previous provider. Kick chat payload contracts must be formalised.
4. **BetterTTV Channel Mapping** – confirm if Kick-specific identifiers become available; until then we fall back to stored identifiers where provided.
5. **Asset Refresh** – Legacy imagery, color palettes, and copy remain throughout the resources folder.
6. **Automated Tests** – expand coverage to integration-level tests that mock Kick REST and websocket flows end-to-end.
7. **Token Encryption** – evaluate migrating token persistence to EncryptedSharedPreferences or Jetpack DataStore for production readiness.
8. **Account Metadata** – Kick OAuth responses do not return profile identifiers; downstream features still rely on tokens stored in legacy preferences.
