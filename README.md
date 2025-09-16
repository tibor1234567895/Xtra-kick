# Xtra for Kick

Xtra is an Android client that is being migrated to the [Kick public API](https://docs.kick.com). This repository now ships Kick-native network clients, OAuth helpers, and chat plumbing so the remaining UI can finish the service switch without relying on any Twitch infrastructure.

## Prerequisites

1. Register an application on the [Kick developer portal](https://docs.kick.com/getting-started/kick-apps-setup) and note the client credentials and redirect URI.
2. Export the required environment variables **before** invoking Gradle so they can be compiled into `BuildConfig`:

```bash
export KICK_CLIENT_ID="your-client-id"
export KICK_CLIENT_SECRET="your-client-secret"
export KICK_REDIRECT_URI="your://redirect"
export KICK_SCOPES="chat:read chat:write channel:read"
```

The defaults for the public API, OAuth server, and chat websocket come from the official docs:

- API base URL: `https://api.kick.com/public/v1`
- OAuth host: `https://id.kick.com`
- Chat websocket: `wss://ws.kick.com/v2`

## Building & Testing

Use the helper script to perform a clean build, unit tests, and lint:

```bash
./scripts/run_checks.sh
```

You can also invoke Gradle directly:

```bash
./gradlew clean assembleDebug
```

## Kick Integration Overview

The migration scaffolding introduced in this iteration includes:

- `KickEnvironment` – single source of truth for API hosts, OAuth configuration, and scopes derived from `BuildConfig`.
- `KickOAuthClient` – implements the OAuth 2.1 authorization-code, refresh, client credentials, revoke, and introspection flows (PKCE helpers included).
- `KickTokenStore` – persists tokens, scopes, and expiry information in shared preferences (follow-up planned for encryption) for reuse across API and chat clients.
- `LoginActivity` – rewired to the Kick OAuth authorization-code flow with PKCE, storing credentials via `KickTokenStore` after successful login.
- `KickApiClient` – strongly typed REST client for `/livestreams`, `/categories`, `/channels`, and `/users`, including paging/query helpers and header management.
- `KickChatClient` – websocket connector that issues Phoenix-style join payloads against `ws.kick.com` and exposes a listener interface for downstream chat UI components.
- `KickBttvService` – preserves BetterTTV global and channel emotes so Kick chat retains third-party reactions that long-time users expect.
- Comprehensive unit tests for the auth, REST, and chat layers using `MockWebServer` and coroutine test utilities.

## Known Gaps

- UI still references Twitch data sources and needs to be re-wired to consume the new Kick repositories.
- Kick session refresh and account metadata are not yet surfaced beyond token storage.
- Kick chat message schemas require full parsing and presentation logic.
- Some Twitch assets, strings, and documentation remain and should be removed in the next migration pass.

## License

Xtra remains licensed under the [GNU Affero General Public License v3.0](LICENSE).
