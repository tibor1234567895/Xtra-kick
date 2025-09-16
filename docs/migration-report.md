# Twitch → Kick Migration Report

## Surface Mapping

| Twitch Surface | Kick Replacement | Notes |
| -------------- | ---------------- | ----- |
| Twitch GraphQL `streams`, `games`, `users` | `KickApiClient` using `/livestreams`, `/categories`, `/channels`, `/users` | Typed Kotlin models mirror the Swagger schema; pagination helpers still need to be threaded into the UI data sources. |
| Twitch OAuth (implicit & device code helpers) | `KickOAuthClient` + WebView-based Kick login | Authorization-code + PKCE flow now powers `LoginActivity`; background refresh scheduling remains outstanding. |
| Twitch WebView login handling implicit tokens | `LoginActivity` loading Kick OAuth authorize URL | Exchanges auth code for tokens, persists via `KickTokenStore`, and exposes retry via external browser fallback. |
| Twitch chat IRC over WebSockets | `KickChatClient` targeting `wss://ws.kick.com/v2` with Phoenix join envelopes | Message parsing/backfill for emojis, badges, and replies still required. |
| BetterTTV global/channel emotes wired through `PlayerRepository` | `KickBttvService` retaining BetterTTV loading for Kick chat | Supports global sets today and tries Kick + Twitch identifiers for channel lookups so third-party reactions stay available. |
| Twitch token persistence in shared prefs | `KickTokenStore` scoped to Kick credentials | Encrypted storage to be evaluated; follow-up needed to migrate existing data safely. |
| Twitch config constants spread across helpers | `KickEnvironment` single configuration surface | Remaining modules must consume this to remove hard-coded Twitch hosts. |

## Removed Twitch Artifacts

- GraphQL/Cronet hints pointing at `gql.twitch.tv` and Twitch CDN hosts inside `XtraModule`.
- README copy, download links, and branding for Twitch.
- Legacy Twitch login workflow and manual token entry paths.
- Build configuration fields now derive Kick endpoints and credentials rather than Twitch tokens.

Additional Twitch-specific models and API wrappers remain and will be excised as the UI migrates to the new Kick repositories.

## Known Gaps & Backlog

1. **UI Integration** – fragment/view-model layers still call the Twitch repositories. They must be updated to use `KickApiClient`, `KickOAuthClient`, and `KickChatClient`.
2. **Playback** – the media player currently depends on Twitch playback token workflows; Kick stream playback endpoints need to be reverse engineered or implemented using documented APIs.
3. **Chat Rich Features** – emoji, badge, and reply parsing still rely on Twitch schemas. Kick chat payload contracts must be formalised.
4. **BetterTTV Channel Mapping** – confirm if Kick-specific identifiers become available; until then we fall back to Twitch IDs where provided.
5. **Asset Refresh** – Twitch imagery, color palettes, and copy remain throughout the resources folder.
6. **Automated Tests** – expand coverage to integration-level tests that mock Kick REST and websocket flows end-to-end.
7. **Token Encryption** – evaluate migrating token persistence to EncryptedSharedPreferences or Jetpack DataStore for production readiness.
8. **Account Metadata** – Kick OAuth responses do not return profile identifiers; downstream features still rely on Twitch tokens stored in legacy preferences.
