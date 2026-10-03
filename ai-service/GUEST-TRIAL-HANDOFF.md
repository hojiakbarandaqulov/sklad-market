# Guest AI trial

Scope: `feature/ai-chat-memory` (backend), `feat/ai-agent` (web), `feat/mobile-ai` (mobile). No main-branch changes. Existing account AI endpoints, role quotas and business services stay authenticated and unchanged.

## Behaviour

- Guests can ask about the marketplace and search public products/companies. They cannot access account data, saved account conversations, seller/admin tools or actions/drafts.
- Default **5 total messages per browser/app installation**, not per day or per chat. Admin or Super Admin can set **0–20** in the existing AI usage controls / mobile AI requests tab. **0 disables new trial messages.** Changes apply to existing guests without resetting their usage.
- Page entry only reads status. First Send issues a random 256-bit guest credential, persisted separately from account tokens. Only its SHA-256 hash is stored on the server. Reloading or signing out does not reset the allowance. Guests never create normal account conversations.
- Every accepted request counts, including provider failures/timeouts. Retries retain the request ID: completed requests return the saved reply, pending requests report busy, failed/expired requests are not executed again. This avoids double generation and unbounded paid retries.
- Maximum 2,000 input characters, 1,024 output tokens per model call, 3 model calls per turn, 2 tool calls per model response, 3 public results per search and one compact result panel. Guest workers have their own 2-thread/4-queued-task pool.
- Only 3 recent exchanges are shown/sent as context. Guest content becomes inaccessible after 24 hours; an hourly, bounded cleanup removes expired content. Small hashed quota/idempotency records remain so cleanup does not reset trials. Registered-user history cleanup is unaffected.
- Replies follow the selected EN/RU/UZ language. Web/mobile show remaining messages, a waiting state, grounded cards and Register / Log in links; the composer is replaced by the sign-in prompt when credits run out. Guest replies arrive as complete messages (registered-user streaming is unchanged).
- Logging in opens the user's own existing AI history. Guest chats are not silently imported into an account.

## Deploy backend first

1. Preserve all environment files and existing databases. Back up the AI database normally; do not reset volumes, schemas or Flyway history.
2. Build/restart **ai-service only** from this branch using the team's existing deployment procedure. Flyway adds `V14__guest_ai_trial.sql`: four new AI-owned tables, no changes to business/account tables. Do not roll out the web/mobile changes against an old backend.
3. The gateway already forwards `/api/v1/ai/**`; no gateway source change is needed. Permit the new `X-AI-Guest-Token` request header through any external proxy/CORS rules. Never log this header or cache guest responses. Use HTTPS in production.
4. Configure **`AI_GUEST_TRUSTED_PROXIES`** with comma-separated **literal IPs** of the actual trusted gateway/proxy hops, if proxied. It is empty by default, so forwarded headers are ignored. The gateway must append its real peer to `X-Forwarded-For`, and the edge must strip/replace untrusted forwarding headers. Do not trust arbitrary client IPs or expose a trusted internal proxy path publicly. Without this configuration, the safe default shares the IP limit among callers behind the gateway.
5. Optional backend environment limits: `AI_GUEST_IP_DAILY_MESSAGES=30`, `AI_GUEST_GLOBAL_DAILY_MESSAGES=1000`. Either set to 0 blocks new guest generation. These are extra UTC-day abuse caps, not the user-visible total allowance. Session creation is capped at 20/IP/day and 2,000 globally/day; sends also have 3/minute/credential. Keep proxy response timeout above 180 seconds for the bounded JSON generation endpoint.
6. Build web (`VITE_FEATURE_AI_AGENT=true`) and mobile from their AI branches. Use the existing API base URLs/auth configuration; no provider secrets belong in clients.

Anonymous visitors cannot be reliably identified as unique people. Clearing app/browser storage, reinstalling or switching devices can obtain another trial; server-side IP/global caps reduce abuse but do not provide person-level identity. Shared networks may also share the IP cap. Add edge rate limiting/CAPTCHA if public traffic requires stronger bot protection. Existing signed-in endpoints are not relaxed to solve this.

## API

- `GET /api/v1/ai/guest/status`: optional `X-AI-Guest-Token`, policy/remaining/recent messages; no row creation or AI call.
- `POST /api/v1/ai/guest/session`: random capability returned once; call only on first Send.
- `POST /api/v1/ai/guest/messages`: capability header + `{ "requestId": "UUID", "content": "..." }`, optional `Accept-Language: EN|RU|UZ`; JSON response includes authoritative state and answer.
- `GET/PUT /api/v1/ai/admin/guest-trial`: real authenticated Admin/Super Admin only; PUT `{ "messageLimit": 5 }`.
- Errors use `X-AI-Error-Code`: `guest_limit_reached`, `guest_busy`, `guest_request_recorded`, `guest_rate_limited`, `guest_invalid`, `guest_unavailable`. Guest clients do not invoke account token refresh/logout on guest errors.

## Focused verification

Run the disposable PostgreSQL tests on a machine with Docker before deployment:

```powershell
cd ai-service
.\gradlew.bat test --tests '*GuestTrialIntegrationTest' --tests '*GuestAiControllerTest' --tests '*GuestAnswerServiceTest' --max-workers=1 --no-daemon
```

These integration tests create/drop only a Testcontainers disposable database. Do not point them at the team's DB.

Manual smoke check on web and a phone: browse as a guest, open AI (no credits spent), ask a catalog question, reload and see the same allowance/history, exhaust it, see Register/Log in, log in and confirm the normal account history/AI still works. Change the guest limit as Admin and verify it takes effect; Buyer/Seller must receive 403 from the admin API. Check a second tab and timeout retry do not cause duplicate generation. Confirm real client IP resolution at the proxy.

Local verification: Java compilation, targeted security/chat tests and web build; mobile changed files checked with the installed Expo/Babel preset. Docker/database integration, live model calls and physical-device execution require the team's runtime. No existing database or environment files were modified.
