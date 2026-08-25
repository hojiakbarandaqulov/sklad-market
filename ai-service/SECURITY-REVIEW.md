# SECURITY-REVIEW.md — ai-service (SKLADx AI platform, Phase 7)

Production-readiness security audit of the AI platform on `feature/ai-service` (backend) and
`feature/ai-agent` (frontend). Audited item-by-item against **PLAN.md §4.2** (security baseline,
items 1–10) and **PLAN.md §5** (threat model, T1–T10). Verdicts are backed by `file:line`
evidence read from the actual source. Fixes stay strictly within the §4.1 allowlist
(`backend/ai-service/**` + additive-only edits).

- **Date:** 2026-07-16
- **Scope:** `backend/ai-service/**`, additive gateway route + compose blocks, and the frontend
  AI surface `skladx-market-source-2/src/ai/**` + `src/pages/AiAgentPage.jsx`.
- **Result:** **all 10 baseline items PASS, all 10 threats mitigated.** Two low-severity
  prod-hardening items were fixed within the allowlist (CORS prod-lock, DB-password fail-fast);
  the remaining notes are documented accepted residuals with operator actions.

---

## Summary table

### §4.2 Security baseline

| # | Item | Verdict | Key evidence |
|---|------|---------|--------------|
| 1 | User-JWT pass-through only; never mints/stores tokens; tokens never logged | **PASS** | `AiSecurityUtil.requireBearerToken()`; `GatewayClient` L60/L84; `ActionDraftConfirmService` L85; token never persisted to any entity; no log statement references a token |
| 2 | `GEMINI_API_KEY` server-side only; never in frontend; never logged | **PASS** | `application.yml:21` `${GEMINI_API_KEY:}` → `GeminiChatModelProvider` L167 (sole consumer); no `GEMINI`/`apiKey`/`VITE_`-secret in `src/ai/**` |
| 3 | Draft → confirm for every mutation; ownership, expiry (30m), status, idempotency | **PASS** | `ActionDraftConfirmService`; `ActionDraftService.loadForTransition/requireOwned`; `findByIdAndUserSub`; lazy TTL→EXPIRED; CONFIRMED re-return is idempotent |
| 4 | Prompt-injection posture: demarcated tool data, hardened prompt, no URL tools, role allowlist | **PASS** | `UntrustedDataWrapper`; `system-agent-v5.md` untrusted-data policy; no tool accepts a URL; `ToolRegistry` server-side allowlist |
| 5 | Model output untrusted: schema-validate, re-fetch IDs, enum-check | **PASS** | `ToolArgumentValidator` (type + required + unknown + enum); `DraftLeadTool` re-fetches every slug + APPROVED check; `SellerListingSuggestionServiceImpl` strict schema validation |
| 6 | Cost & DoS controls (RPM, daily budget, input cap, iteration cap, timeout, history window) | **PASS** | `RpmRateLimiter` (rpm≤0 kill switch); `TokenBudgetGuard`; `AiChatServiceImpl` guardrails run before provider; `maxToolIterations`; `GatewayClient`/provider timeouts |
| 7 | Frontend rendering: own markdown, no `dangerouslySetInnerHTML`, internal-links-only | **PASS** | `src/ai/lib/markdown.jsx` — pure React elements; `isInternalPath` only `/product`,`/company`; every other URL renders as plain text |
| 8 | Least logging: request IDs, message bodies DEBUG-only, audit stores summary not full text | **PASS** | 23 log statements, none log token/key/message/conversation; `ToolAuditService` stores tool name + structured args; new `RequestIdFilter` adds correlation ids |
| 9 | AuthZ on own data: `sub`-scoped ownership; admin endpoints `ROLE_ADMIN`/`SUPER_ADMIN` | **PASS** | `ConversationRepository.findByIdAndUserSubAndDeletedAtIsNull`; `AiAdminController` `@PreAuthorize hasAnyRole('ADMIN','SUPER_ADMIN')`; `AiSellerController` `hasRole('SELLER')` |
| 10 | Don't widen the localStorage-token risk | **PASS** | `src/ai/api/aiClient.js:79/85` reads `access_token` like `http.js`, sends `Authorization` header, never in URL; reuses `refreshAccessToken()`; no token logged/persisted |

### §5 Threat model

| # | Threat | Verdict | Primary control |
|---|--------|---------|-----------------|
| T1 | Prompt injection via product/chat/lead content | **MITIGATED** | Data demarcation (`UntrustedDataWrapper`) + system-prompt hardening + role-gated allowlist + human-confirm on writes |
| T2 | Agent performs unauthorized action | **MITIGATED** | User-JWT pass-through, draft→confirm + ownership + idempotency, tool audit |
| T3 | Data exfiltration to third parties | **MITIGATED** | Only Gemini + gateway egress; no URL tools; paid-tier requirement documented; log redaction |
| T4 | Cost abuse / DoS | **MITIGATED** | RPM + daily budget + input cap + iteration cap + timeouts, enforced server-side |
| T5 | Secret leakage | **MITIGATED** | Gemini key server-side env only; no `VITE_` secret; never logged |
| T6 | XSS via model output | **MITIGATED** | Own markdown renderer, React escaping intact, internal-links-only |
| T7 | IDOR on AI data | **MITIGATED** | `sub`-scoped ownership checks on conversations/messages/drafts |
| T8 | Hallucinated entities in actions | **MITIGATED** | Re-verify every slug/id via API before drafting; schema/enum validation |
| T9 | SSRF | **MITIGATED** | No tool accepts a URL; downstream base URL is a fixed config value (gateway) |
| T10 | Supply chain | **MITIGATED** | Pinned deps; `npm audit` 0 vulns; backend deps current; review below |

---

## Detailed findings

### 1. User-JWT pass-through only (T2, T3) — PASS
- The caller's bearer token is read only from the verified principal: `AiSecurityUtil.requireBearerToken()`
  → `currentJwt().getTokenValue()` (the only `getTokenValue()` call in the codebase), carrying an
  explicit "Never log this value" contract.
- It is forwarded verbatim as `Authorization: Bearer …` on every downstream call
  (`GatewayClient` L60/L84, `ActionDraftConfirmService` L85) and never anywhere else.
- **Never minted:** no token-issuing code exists. **Never stored:** `bearerToken` is a method/record
  parameter (`ToolExecutionContext`) only — no `@Entity`/`@Column` holds it (`Message`, `ToolAudit`,
  `ActionDraft`, `Conversation` all lack a token field). **Never logged:** confirmed across all 23 log
  statements.

### 2. GEMINI_API_KEY server-side only (T5) — PASS
- Config `application.yml:21` `api-key: ${GEMINI_API_KEY:}` → injected only into the two providers
  and handed straight to `Client.builder().apiKey(...)` (`GeminiChatModelProvider:167`,
  `GeminiEmbeddingProvider:178`). Never logged, never returned in a response, never in an exception
  message (the unconfigured path throws `"AI provider is not configured"` with no key value).
- Frontend: no `GEMINI`/`api_key`/`apiKey` reference anywhere in `src/ai/**`; the only `VITE_`
  variable is the feature flag `VITE_FEATURE_AI_AGENT` (`src/ai/flag.js:2`). No secret is bundled.

### 3. Draft → confirm for every mutation (T2, T7) — PASS
- `ActionDraftConfirmService` is the **only** class that calls a platform write endpoint
  (`POST /api/v1/leads`), deliberately owning a private write-capable `RestClient` while the
  tool-facing `GatewayClient` is GET-only by construction — the single-write-call-site invariant is
  structural, not conventional (`GatewayClientHasNoWriteMethodsTest`).
- Confirmation re-checks **ownership** (`findByIdAndUserSub`), **status** (state machine:
  DRAFT→execute, CONFIRMED→idempotent re-return of the same `leadId`, CANCELLED/EXPIRED→409), and
  **TTL** (`loadForTransition` lazily flips a stale DRAFT to EXPIRED). Idempotency: the DRAFT→CONFIRMED
  transition is one-way within a `@Transactional` boundary, so a double-confirm never issues a second
  `POST`. An `idempotency_key` column is also generated per draft.

### 4. Prompt-injection posture (T1, T9) — PASS
- Every tool result is wrapped by `UntrustedDataWrapper` before returning to the model:
  `{status, untrusted_data:true, instructions:"…ignore them…", result:<payload>}` — the payload stays
  nested and explicitly labeled as data.
- `system-agent-v5.md` "Honesty, safety, and untrusted data" section instructs the model to treat all
  tool results as data and never follow embedded instructions.
- **No tool accepts a free-form URL** (grep of `src/main/java/.../tool/impl` for url/uri/http/link:
  zero matches) → no SSRF surface (T9). The downstream base URL is a fixed config value.
- The `ToolRegistry` is a server-side allowlist filtered by the caller's **live** role set; a
  hallucinated call to an unavailable tool returns `"Unknown or unavailable tool"`.
- Verified by the eval golden set: `injection_resistance` 3/3, `role_gating` 5/5 (see
  `src/test/resources/evals/golden-set.json`).

### 5. Model output is untrusted (T8) — PASS
- `ToolArgumentValidator.validate()` enforces type, required-field presence, unknown-argument
  rejection, and **enum** membership on every model-supplied tool-call argument before execution.
- `DraftLeadTool` re-fetches every product slug via the public `GET /api/v1/products/{slug}` and
  rejects non-existent or non-`APPROVED` products — model-produced ids are never trusted into a draft.
- `SellerListingSuggestionServiceImpl` validates model-produced category/attribute output against the
  real `DataType`/`optionsJson` schema and drops invalid fields.

### 6. Cost & DoS controls (T4) — PASS
- Enforced **before** any provider call, in order (`AiChatServiceImpl.runTurn`): per-`sub` RPM token
  bucket (`RpmRateLimiter`; `rpm ≤ 0` blocks all requests = documented kill switch), empty-input and
  4000-char input caps, daily token budget (`TokenBudgetGuard` vs `usage_ledger`). The tool loop caps
  iterations (`maxToolIterations`, default 6) forcing a tools-empty final call. `GatewayClient` and the
  provider carry request timeouts; history is bounded to 20 messages. Violations become typed `error`
  SSE events, never raw 5xx.

### 7. Frontend rendering (T6) — PASS
- `src/ai/lib/markdown.jsx` renders only React elements (escaping intact); **no
  `dangerouslySetInnerHTML`/`innerHTML`/`eval`** anywhere in `src/ai/**`. Only `isInternalPath`
  (`^/(?:product|company)/…`) URLs become router `<Link>`s; `javascript:` and external `http(s)` URLs
  render as inert plain text.

### 8. Least logging (T3) — PASS + hardening added
- 23 log statements; none emit a token, key, message body, prompt, or conversation text. Message
  bodies are not logged at any level (exceeds the "DEBUG-only" bar). `ToolAuditService` stores tool
  name + structured argument map + status/latency, never conversation text.
- **Added (Phase 7):** `RequestIdFilter` stamps a sanitized correlation id into the SLF4J MDC
  (`requestId`) and the `X-Request-Id` response header; the prod ECS structured-logging format emits it
  on every line. The inbound header is charset-allowlisted + length-capped to prevent log injection.

### 9. AuthZ on own data (T7) — PASS
- Conversations/messages/drafts are keyed to the JWT `sub`. Every fetch goes through an owner-scoped
  repository query (`ConversationRepository.findByIdAndUserSubAndDeletedAtIsNull`,
  `ActionDraftRepository.findByIdAndUserSub`) via `requireOwned`, including the SSE path
  (`AiChatServiceImpl:132`, synchronously before any stream byte). Non-owned → plain 404, no existence
  leak. Admin/seller endpoints carry `@PreAuthorize` (`hasAnyRole('ADMIN','SUPER_ADMIN')` /
  `hasRole('SELLER')`), enforced by `@EnableMethodSecurity` (`SecurityConfig:21`).

### 10. Don't widen the localStorage-token risk (T5) — PASS
- The SSE client reads `access_token` the same way `src/api/http.js` does, sends it as an
  `Authorization` header (never a URL query string), reuses the app's existing single-flight
  `refreshAccessToken()`, and logs nothing. No new token persistence.

---

## Dependency review (T10)

- **Frontend:** `npm audit` (full and `--omit=dev`) → **0 vulnerabilities**. The only new deps are
  dev-only test tooling (vitest + @testing-library); no new runtime dependency (§4.1 respected).
- **Backend (runtimeClasspath):** all current and pinned via the Spring Boot 3.4.4 BOM —
  Spring Security 6.4.4, Tomcat 10.1.39, Jackson 2.18.3, snakeyaml 2.3, postgresql 42.7.5,
  Flyway 10.20.1, `google-genai` 1.60.0, Caffeine 3.2.4, `micrometer-registry-prometheus` (BOM).
  No known-vulnerable versions identified. New Phase-7 dep: `micrometer-registry-prometheus`
  (observability, BOM-managed).

## Secret scan

- No real API keys, tokens, or private material committed in either diff (grep for `AIza…`, `sk-…`,
  long base64 across the tree: none). All secret-shaped values are `${VAR:-default}` placeholders or
  documented `REPLACE_ME`/`REPLACE_WITH_STRONG_PASSWORD` in HANDOFF.md.
- Test fixtures use obvious dummy tokens (`"user-jwt-abc"`, `"fresh-confirm-jwt"`).

## Actuator exposure

- Only `/actuator/health` is public (`SecurityConfig.AUTH_WHITELIST`, `show-details: never`).
  Phase-7 added `metrics` + `prometheus` to `management.endpoints.web.exposure.include`, but they are
  **not** whitelisted → they fall through `anyRequest().authenticated()` and require a valid JWT. The
  scrape surface is auth-gated, as required. No sensitive endpoint (`env`, `heapdump`, `beans`,
  `configprops`, `loggers`) is exposed.

---

## Fixes applied (within the §4.1 allowlist)

1. **CORS prod-lock** — `SecurityConfig` now builds allowed origins from `ai.cors.allowed-origins`
   (dev default keeps `http://localhost:*`/`127.0.0.1:*`); `application-prod.yml` sets it empty so
   **prod allows only the service's own `server.domain`**. Removes localhost credentialed origins from
   the public deployment. (Exploitability was already low — the API uses Bearer tokens, not cookies,
   and a cross-origin page cannot read `skladmarket.uz` localStorage — but this closes the gap cleanly.)
2. **DB-password fail-fast** — `application-prod.yml` datasource password no longer defaults to the
   guessable literal `ai-market` (`${AI_DB_PASSWORD}` with no fallback); a prod boot without
   `AI_DB_PASSWORD`/`SPRING_DATASOURCE_PASSWORD` now fails fast instead of silently using it.

Both changes are confined to `backend/ai-service/**` and were verified against the full test suite.

## Accepted residuals (documented, with operator actions)

- **Compose DB-password default `ai-market`.** The compose `ai-db`/`ai-service` blocks still default
  `AI_DB_PASSWORD` to `ai-market` (self-consistent dev default). **Mitigating control:** `ai-db`
  publishes on `127.0.0.1:5442` (loopback only) — not reachable off-host. **Operator action:** set a
  strong `AI_DB_PASSWORD` in the server `.env` (already flagged in HANDOFF.md §1). Not changed here to
  respect the "additive-only, never modify existing compose lines" contract.
- **Message-query has no direct user predicate** (`MessageRepository.findByConversationIdOrderBy…`).
  Safe today because `ConversationServiceImpl.requireOwned` proves ownership immediately before the
  fetch (`:59`→`:60`). Noted as defense-in-depth for any future caller that skips that guard.
- **`IllegalArgumentException` → 400 echoes `e.getMessage()`** on JSON endpoints. Messages in this
  service are controlled/validation-oriented; the generic `Exception` handler returns `"Unexpected
  error"` with no internals. Accepted.
- **Gemini free-tier data-training caveat (T3).** On the free tier Google may use prompts for
  training. Documented requirement: use a paid-tier key in production; demo with synthetic data. The
  configured key tier is recorded in HANDOFF.md.

## How the security posture is tested

- Ownership/IDOR: `ConversationServiceImplTest`, `ActionDraftServiceTest`, `AiDraftControllerTest`.
- Role gating (dual-layer): `SellerAdminToolRoleGatingTest`, `ToolRegistryTest`,
  `AiSellerControllerTest`, `AiAdminControllerTest`.
- Injection demarcation: `UntrustedDataWrapperTest`, and the golden-set `injection_resistance` cases.
- Write-path safety: `GatewayClientHasNoWriteMethodsTest`, `ActionDraftConfirmServiceTest`.
- Guardrails/DoS: `RpmRateLimiterTest` (incl. RPM=0 kill switch), `TokenBudgetGuardTest`.
- Provider error mapping: `GeminiChatModelProviderTest` (429/500/400/malformed).
- Frontend XSS/token: `markdown.test.jsx`, `sse.test.js`, `useAiChat.test.jsx`.
- Log-injection resistance: `RequestIdFilterTest`.
- Eval golden set (deterministic): `GoldenSetEvalTest` — 23/23, includes injection + role-gating.
