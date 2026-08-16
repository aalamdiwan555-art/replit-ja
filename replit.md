# AUTOPILOT

An Android screen-automation app with network-synced access control, a first-launch ad-free trial, compact floating controls, and admin-managed entitlements.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `app/src/main/java/com/autopilot/app/NetworkTimeProvider.kt` — network Date-header sync with monotonic uptime fallback.
- `app/src/main/java/com/autopilot/app/SecureStorage.kt` — local entitlement, trial, reward-session, and admin override state.
- `app/src/main/java/com/autopilot/app/AutopilotApp.kt` — Compose dashboard, controls, reward flow, expiry lock, and secret admin entry.
- `app/src/main/java/com/autopilot/app/ScreenCaptureService.kt` — MediaProjection lifecycle and periodic hard-expiry enforcement.
- `app/src/main/java/com/autopilot/app/FloatingControlPanel.kt` — compact icon-only overlay controls.

## Architecture decisions

- Entitlement calculations use a network-derived clock anchored to `SystemClock.elapsedRealtime()`; the device wall clock is never used for access decisions.
- If a subscription cannot be validated after a reboot, controls fail closed until network time is available.
- Reward sessions are user-initiated and return-confirmed; the app does not automate or click ads.
- Lifetime access and admin ad-free mode are separate flags so an administrator can remove ads without changing expiry semantics.

## Product

- Grants every new install a one-hour ad-free trial.
- Shows a live trial/subscription countdown and locks controls when access expires.
- Adds voluntary ad-view sessions that grant one extra day after ten completed sessions.
- Provides an icon-only floating capture control panel and hidden multi-tap admin controls.

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Gotchas

- The Android SDK must be configured for `./gradlew :app:assembleDebug`; the current development environment does not provide a writable SDK location.
- `GITHUB_PERSONAL_ACCESS_TOKEN` is used only through an ephemeral Git HTTP header when pushing; it is not stored in repository config.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
