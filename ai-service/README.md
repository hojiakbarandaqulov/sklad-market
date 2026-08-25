# ai-service

Isolated AI platform service for SKLADx (chat agent, grounded marketplace tools, action
drafting, semantic search). See `PLAN.md` at the project root for the full build plan; this
service is built phase by phase, and each phase's session updates this file and `HANDOFF.md`.

## What it owns

- Standalone Spring Boot 3.4.4 / Java 17 Gradle service (Spring Cloud 2024.0.1, Eureka client),
  built and deployed exactly like every other backend service: shared `../Dockerfile-service`
  with `SERVICE_DIR=ai-service` — **no Dockerfile of its own**.
- Its own Postgres: `ai-db` (`pgvector/pgvector:pg16`), host port **5442**, database
  `skalad_market_ai`, credentials `AI_DB_USERNAME` / `AI_DB_PASSWORD` — **separate from** the
  platform's shared `DB_USERNAME` / `DB_PASSWORD` on the host Postgres (port 5432), which
  ai-service never connects to.
- Schema managed by Flyway (`src/main/resources/db/migration`), `ddl-auto: validate`.
- Port **8091** (8090 is taken by `file-service`).
- Gateway route: `/api/v1/ai/**` → `lb://ai-service` (additive block in
  `../api-gateway/src/main/resources/application.yml`).

## Isolation contract

This service is being built under a strict isolation contract (see root `PLAN.md` §4): it may
only create new files under `backend/ai-service/**`, and may only make **additive** edits
(new blocks, never modifying existing lines) to `../api-gateway/.../application.yml` and
`../docker-compose.yml`. No other existing backend file is touched. No Kafka. No connection to
the shared host Postgres on port 5432. Every downstream call to another service uses the calling
user's own JWT — no service account, no privileged access.

## Local development

Requires a local `ai-db` container (see root `docker-compose.yml`'s `ai-db` block) and a local
Keycloak (shared with the rest of the stack, `application-local.yml` points at
`http://localhost:9090/realms/market-realm`).

```bash
docker compose -f ../docker-compose.yml up -d ai-db
cd ai-service
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Health check: `curl http://localhost:8091/actuator/health` → `{"status":"UP"}`.
Ping (requires a Bearer token once Keycloak is reachable): `curl -H "Authorization: Bearer <jwt>" http://localhost:8091/api/v1/ai/ping`.

## Endpoints

- `GET /actuator/health` — public (whitelisted).
- `GET /api/v1/ai/ping` — authenticated; returns `ApiResponse<{service,status,time}>`.
- `POST /api/v1/ai/conversations` — authenticated; create a conversation, `ApiResponse<ConversationDto>`.
- `GET /api/v1/ai/conversations` — authenticated; paged list of the caller's own conversations.
- `GET /api/v1/ai/conversations/{id}/messages` — authenticated, owner-scoped; paged message history.
  The full AI role snapshot and every persisted tool name are re-authorized against the current JWT;
  a revoked role makes the role-derived conversation unavailable instead of replaying old summaries.
- `DELETE /api/v1/ai/conversations/{id}` — authenticated, owner-scoped; soft delete.
- `POST /api/v1/ai/conversations/{id}/messages` — authenticated, owner-scoped; **SSE stream**
  (`text/event-stream`, `X-Accel-Buffering: no`, `Cache-Control: no-cache`, 15s heartbeats) of
  `token`/`usage`/`done`/`error` events per PLAN.md §6. Guardrail and provider failures always
  surface as a typed `error` event inside the stream, never a raw 5xx.
- `POST /api/v1/ai/drafts/{id}/confirm` · `POST /api/v1/ai/drafts/{id}/cancel` — authenticated,
  owner-scoped (Phase 4); confirm is the only path that creates a real lead.
- `GET /api/v1/ai/drafts/{id}` — authenticated and owner-scoped; returns authoritative current
  status and restores a still-pending card after chat reload without trusting a stale snapshot.
- `GET /api/v1/ai/search?q=&limit=` — authenticated; **semantic (cross-lingual) product search**
  over the embedding index, `ApiResponse<{query,count,items:[{slug,name,price,currency,regionId,
  categoryId,score}]}>` (Phase 5). Queries the local vector table only — no downstream call.
- `GET /api/v1/ai/similar/{productId}?limit=` — authenticated; **content-based "similar products"**
  (vector neighbours, same-category boost), same item shape; 404 if the product isn't indexed
  (Phase 5). Documented as a **consumable API for the frontend team** — wiring it into `ProductPage`
  is their call (PLAN.md §8); ai-service does not touch their frontend.
- `POST /api/v1/ai/admin/reindex` · `GET /api/v1/ai/admin/reindex/status` — `ROLE_ADMIN`/
  `ROLE_SUPER_ADMIN` only (Phase 5); trigger a catalog re-embed and read the last run's status.
- `GET|PUT|DELETE /api/v1/ai/admin/rate-limits[/{userSub}]` — `ROLE_ADMIN`/
  `ROLE_SUPER_ADMIN` only; list, override, or reset per-user chat requests per minute. Users are
  registered inside the AI database when they chat; no core user-service schema is modified.
- `POST /api/v1/ai/seller/suggest-listing` — `ROLE_SELLER` only (Phase 6); vision-assisted
  category + attribute suggestion, strictly validated against the real category schema (copy-to-form,
  never auto-submit).
- `GET /actuator/health` — public. `GET /actuator/metrics` · `GET /actuator/prometheus` —
  **authenticated** (Micrometer scrape surface, Phase 7). No other actuator endpoint is exposed.

## AI chat (Phase 1)

- `ChatModelProvider` / `GeminiChatModelProvider` (`org.example.ai.provider`) wrap
  `com.google.genai:google-genai` (verified 1.60.0). The SDK's own default retry (5 attempts,
  blocking backoff, loses the real HTTP status) is disabled via `HttpRetryOptions.attempts(1)`;
  the provider does its own single retry with jitter on the initial call, then maps the real
  exception to one of the SSE protocol's error codes.
- Guardrails (`org.example.ai.guardrail`) run before every generated chat turn: a Caffeine-backed
  per-user token-bucket RPM limiter (`AI_RATE_LIMIT_RPM` default, with AI-admin per-user overrides;
  `0` is a hard kill switch), a daily
  token budget check against `usage_ledger` (`AI_DAILY_TOKEN_BUDGET`), a 4000-char input cap, and
  a 20-message history window. Read-only dashboard search, semantic search, and similar-product
  endpoints do not consume either the chat RPM bucket or the daily usage ledger.
- System prompt v1 lives at `src/main/resources/prompts/system-agent.md`: SKLADx identity and
  scope, replies in the user's message language (uz/ru/en, Russian default), states plainly that
  it cannot browse the catalog yet (no tools until Phase 2), and treats all quoted platform
  content as untrusted data.
- `GEMINI_API_KEY` is read lazily on first use, not at application startup — the service still
  boots and passes health checks with no key configured; an actual chat turn without a key fails
  as a typed `provider_error` SSE event instead of a context-startup failure.

## Embeddings & semantic search (Phase 5)

- `EmbeddingProvider` / `GeminiEmbeddingProvider` (`org.example.ai.provider`) wrap the same
  `com.google.genai` SDK for `gemini-embedding-001`: one `batchEmbedContents` call per batch,
  `output_dimensionality: 768` (MRL-truncated), and every vector **L2-renormalized** so cosine math
  is correct (`task_type` RETRIEVAL_DOCUMENT for the index, RETRIEVAL_QUERY for queries).
- **Vectors are stored/queried via plain JDBC** (`org.example.ai.embedding.ProductEmbeddingRepository`)
  — there is no pgvector Hibernate type on this stack. Vectors bind as `CAST(? AS vector)` text
  literals (never string-concatenated); ranking uses pgvector's `<=>` cosine operator with an HNSW
  `vector_cosine_ops` index (`product_embedding`, Flyway `V4`).
- The **content-hash indexer** (`ProductIndexer`) paginates the PUBLIC `GET /api/v1/products/all`
  (credential-free `PublicCatalogClient` — the indexer has no user context and never sends a token),
  indexes only publicly-visible products (`status == APPROVED && isActive == true` — mirrors
  product-service's own catalog-visibility rule; `/api/v1/products/all` itself applies no such
  filter), skips unchanged rows by SHA-256 content hash, and removes rows for products that lost
  visibility or disappeared. It runs on a **dedicated scheduler/thread pool** (`aiIndexerScheduler`),
  never the chat pools, so an indexing failure is its own domain and cannot degrade chat — each run
  is recorded in `index_state` and failures are contained, never thrown. Trigger it manually with
  `POST /api/v1/ai/admin/reindex` (admin only) and read the last run via `.../reindex/status`.
- Two agent tools expose this to the chat agent: `semantic_search_products` (use when keyword
  search is weak or the query is cross-lingual/conceptual) and `find_similar_products` (by slug).
  System prompt **v4** (`prompts/system-agent-v4.md`) teaches keyword-first, semantic-as-fallback.

### Runbook: reindex & tuning

- **When it runs:** `AI_INDEXER_INITIAL_DELAY_MS` after boot, then every `AI_INDEXER_INTERVAL_MS`
  (fixed delay after the previous run finishes — runs never overlap themselves). Set
  `AI_INDEXER_ENABLED=false` to disable the scheduled run entirely (existing vectors keep serving
  search/similar). A manual admin reindex is skipped (not queued) if a run is already in progress.
- **Cost:** each changed product is one embedded document; unchanged products cost nothing
  (hash-skip). Batch size is `AI_EMBEDDING_BATCH_SIZE` (texts per Gemini call). `GEMINI_API_KEY` is
  required for indexing/search; without it the indexer records a `FAILURE` run and chat is untouched.
- **Similar-category boost:** `ai.similar.category-boost` (default `0.05`) nudges same-category
  products up in the "similar" ranking.

## Business discovery and privacy-safe matchmaking

- Flyway `V5` adds a public-company capability index (`company_embedding`, HNSW plus
  category/region indexes). It embeds public company names plus verification status with their
  approved, active product catalog; supplier recommendations then require `VERIFIED`. Public
  phone/website/address fields are not embedded or indexed and are stripped from persisted
  structured-result snapshots. They are hydrated live from the already-public company-by-slug
  endpoint for shortlisted results; owner-visible assistant prose can still mention a live result.
- `GET /api/v1/ai/business-search` returns typed PRODUCT/COMPANY results. Semantic and local
  name/slug matches are fused. If the separate AI index is empty, unfiltered dashboard queries
  fall back to the existing read-only public catalog and verified-company endpoints; this makes a
  restored marketplace database immediately searchable while the AI index warms up. Structured
  filters never use a page-filtered approximation. Filters are applied in SQL before `LIMIT`; every response includes index
  `asOf`/staleness metadata, and relevance is a ranking signal rather than a stock guarantee.
  Product price bounds require an explicit three-letter currency; no cross-currency catalog range
  is exposed.
- BUYER-only `POST /api/v1/ai/recommendations/suppliers` uses explicit need text when supplied;
  otherwise its ephemeral preference uses only that caller's cart, favorites, and leads. Supplier
  price ranges are deliberately not ranked or filtered because one catalog can mix currencies;
  compare the returned individual products and their currencies instead.
- SELLER-only `recommend_buyers` ranks only caller-scoped `/api/v1/leads/seller` results and excludes
  buyer identifiers, contacts, comments, and addresses from its projection.
- Flyway `V6` adds opt-in buying intents. A buyer creates a private DRAFT and must explicitly publish
  it with `POST /api/v1/ai/buying-intents/{id}/publish` and
  `{ "publicationConsent": true }` before sellers can search it. `/mine` is paginated with `page`
  and `per_page` (maximum 50). Seller projections omit owner ids and dedicated contact fields;
  buyer-authored free text is best-effort screened and must be reviewed before publication. The
  review includes category, region, need, quantity/unit, budget/currency, and expiry.
- New agent results also stream as typed `result_set` SSE events. Tool-audit and persisted tool
  metadata redact PII-capable/free-text arguments before storage.
- Optional discovery tuning is exposed through `AI_BUSINESS_INDEX_MAX_AGE_MINUTES` and
  `AI_BUSINESS_CONTACT_TIMEOUT_MS`/`MAX_LOOKUPS`/`PARALLELISM`. Buying-intent active caps and bounded
  expiry/retention maintenance use the `AI_BUYING_INTENT_*` settings in `application.yml`.

### Runbook: key rotation, budget tuning, common failures

- **Rotate `GEMINI_API_KEY`:** update the value in the server `.env` and restart ai-service
  (`docker compose up -d ai-service`). The SDK client is built lazily and holds the key only in
  memory, so a restart fully swaps it; no other service is affected. The key is never logged, never
  returned in a response, and never sent to the browser.
- **Tune spend:** `AI_DAILY_TOKEN_BUDGET` caps per-user chat tokens/day (in+out) plus conservative
  embedding-request units for direct semantic/business search against `usage_ledger`;
  lower it to tighten cost. `AI_RATE_LIMIT_RPM` caps per-user requests/minute; `AI_RATE_LIMIT_RPM=0`
  is a hard kill switch (blocks all chat while keeping health/read endpoints up). Watch actual spend
  with the `usage_ledger` cost query in `HANDOFF.md` §4.
- **Common failures:**
  - *Every chat turn returns `provider_error`* → `GEMINI_API_KEY` unset/invalid, or the model name in
    `AI_CHAT_MODEL` is wrong. The service still boots and stays healthy; only chat degrades.
  - *`/actuator/health` = DOWN* → ai-db unreachable. The process stays up and `/api/v1/ai/**` still
    returns typed errors; restore ai-db (`docker compose up -d ai-db`).
  - *Grounded answers say "temporarily unavailable"* → the gateway/downstream service is down; tools
    surface an honest error rather than fabricating data.
  - *`indexSize` stuck at 0 / `lastStatus: FAILURE`* → the indexer can't reach the public catalog or
    `GEMINI_API_KEY` is unset; check `.../reindex/status` notes. Chat is unaffected (separate domain).
  - *Startup fails resolving `AI_DB_PASSWORD`* → the prod profile requires it (no default, by design);
    set it (or `SPRING_DATASOURCE_PASSWORD`, which the compose block supplies).

## Observability

- **Metrics** (Micrometer) via the **auth-gated** actuator (`/actuator/metrics` + `/actuator/prometheus`
  require a JWT; only `/actuator/health` is public). Custom AI meters: `ai.chat.turn.duration{outcome}`,
  `ai.chat.tokens.in`/`ai.chat.tokens.out`, `ai.tool.call.duration{tool,status}`,
  `ai.provider.stream.duration{status}`, `ai.semantic.search.duration{operation,status}`,
  `ai.business.discovery.duration{operation,status}`,
  `ai.indexer.running`, `ai.indexer.embeddings`, plus the auto
  `http.server.requests` and JVM/HikariCP defaults. Registered in `org.example.ai.observability`
  (`AiMetrics`, `IndexerMetrics`). No sensitive actuator endpoint (`env`/`beans`/`heapdump`/`loggers`)
  is exposed.
- **Structured logging:** the **prod** profile emits ECS JSON to stdout
  (`logging.structured.format.console=ecs`, Spring Boot 3.4 native — no extra dependency; local/test
  keep human-readable logs). `RequestIdFilter` stamps a sanitized correlation id into the MDC
  (`requestId`) and the `X-Request-Id` response header on every request; `aiChatExecutor` propagates
  and then clears that MDC context, so async turn logs correlate without cross-task leakage. Message
  bodies, tokens, and keys are never logged.
- **Cost:** authoritative token usage is in `usage_ledger` (per user, per day); see the documented
  cost query in `HANDOFF.md` §4.

## Evaluation harness

- `src/test/resources/evals/golden-set.json` — 28 golden cases across four categories: `tool_selection`
  (keyword vs semantic vs digest vs draft), `injection_resistance`, `refusal`, `role_gating`.
- **Deterministic gate** (`GoldenSetEvalTest`, runs in `./gradlew test` and standalone via
  `./gradlew eval`): asserts the model-independent invariants of each case — expected tool is
  available to the persona, forbidden tools are denied at the registry layer, injection payloads are
  wrapped as untrusted data, and the refusal policy is present. **Threshold: 100%** (currently 28/28).
- **Optional live eval** (`LiveToolSelectionEvalIT`, self-skips unless `AI_EVAL_LIVE=true` +
  `GEMINI_API_KEY`): sends the tool-selection/refusal prompts to the real model and measures actual
  judgment. **Threshold: ≥ 85%.** Never a blocking CI gate (model output is non-deterministic).

## Testing

`./gradlew test` — **328-test inventory** (all 325 runnable tests green; 2 live-embedding and 1
live-eval provider-gated skips). The full invocation found one Mockito-only assertion-harness error;
that test was corrected and passed in isolation. Real PostgreSQL/pgvector Flyway V1–V7 and
repository/locking coverage, MockMvc
security slice tests, WireMock tests against `GeminiChatModelProvider` (success stream,
429-then-retry, persistent 500, non-retried 400, malformed mid-stream chunk, non-streaming
`generate()`, missing-API-key short circuit), ownership-isolation unit tests for
`ConversationService`, guardrail unit tests (rate limiter incl. the RPM=0 kill switch, token
budget), an SSE MockMvc slice test asserting the nginx-safe headers and event payload shape, the
dual-layer role-gating matrix, the write-path-has-no-write-methods reflection test, the golden-set
eval (`GoldenSetEvalTest`), and the observability unit tests (`AiMetricsTest`, `RequestIdFilterTest`).

`./gradlew eval` runs just the golden-set evaluation harness (see "Evaluation harness").

## Phase status

- **Phase 0**: service skeleton, security baseline, Flyway V1 (vector extension +
  `conversation`/`message`/`usage_ledger` tables), ping endpoint, additive gateway route + compose
  wiring.
- **Phase 1**: Gemini chat provider, conversation/message persistence, SSE streaming chat, cost
  guardrails, system prompt v1.
- **Phase 2**: manual Gemini function-calling loop + role-filtered `ToolRegistry`, five read-only
  catalog tools (`search_products`/`get_product`/`list_categories`/`get_catalog_filters`/
  `get_company`), `tool_audit` (Flyway V2), untrusted-data wrapping + injection defenses.
- **Phase 3** (frontend repo): flag-gated streaming chat UI in `src/ai/**`, trilingual i18n, vitest.
- **Phase 4**: action drafts (Flyway V3) — `draft_lead` + confirm/cancel + digest tools; the only
  write path to `POST /api/v1/leads` is `ActionDraftConfirmService`.
- **Phase 5**: embeddings index (Flyway V4, `product_embedding vector(768)` + HNSW), semantic search
  + similar-products endpoints and agent tools, the public-endpoint content-hash indexer on its own
  scheduler, system prompt v4. See "Embeddings & semantic search" above.
- **Phase 6**: seller & admin assist — persona-aware system prompt v5 + seller/admin fragments, live
  per-request role-set gating (`AiSecurityUtil.currentRoleSet()`), six role-gated tools
  (`get_seller_leads`/`draft_lead_reply`/`draft_chat_reply` for SELLER;
  `get_moderation_queue`/`get_reports`/`summarize_moderation_item` for ADMIN/SUPER_ADMIN), and the
  vision-assisted `POST /api/v1/ai/seller/suggest-listing` (strict schema validation). One
  user-approved additive read endpoint in `category-service`
  (`GET /api/v1/categories/{slug}/attributes`) — see `HANDOFF.md` §2.
- **Phase 7**: hardening + productionization — security audit (`SECURITY-REVIEW.md`), CORS prod-lock
  + DB-password fail-fast, Micrometer metrics + prod ECS JSON logging + request-id correlation, the
  golden-set eval harness, resilience-matrix verification, and this doc + `DEMO.md` + the finalized
  `HANDOFF.md`. See "Observability", "Evaluation harness", and "Limitations".

- **Phase 8**: AI business discovery and matchmaking — public company capability index (Flyway V5),
  combined product/company/public-contact search, explainable BUYER supplier recommendations,
  seller-owned lead opportunity ranking, opt-in buying intents whose account/contact columns are
  excluded from seller projections (Flyway V6), and typed structured result cards.

## Limitations

Honest known limitations at hand-off (none block deployment; several are deferred by design in
PLAN.md §8):

- **No live end-to-end (L3) run yet.** The full stack (real Keycloak JWTs, gateway, nginx) runs only
  on the team's server. Everything is verified at L1 (328-test inventory) + L2 (real
  PostgreSQL/pgvector Flyway V1–V7 plus the existing local boot checks); the
  cumulative L3 curl checklist in `HANDOFF.md` §3 runs after the backend team deploys.
- **Live model-quality eval is manual.** The deterministic golden set (28/28) is the CI gate; the
  real-model tool-selection/refusal accuracy (`LiveToolSelectionEvalIT`, threshold 85%) only runs on
  demand with `AI_EVAL_LIVE=true` + a key, since model output is non-deterministic.
- **Gemini free-tier data caveat.** On the free tier Google may use prompts for training; production
  requires a paid-tier key (record the tier in `HANDOFF.md`). Demo with synthetic data.
- **Indexer is poll-based**, not event-driven — new/edited products appear in semantic search within
  one `AI_INDEXER_INTERVAL_MS` cycle, not instantly (Kafka-based incremental indexing is deferred,
  PLAN.md §8).
- **Product-page recommendations degrade safely.** Logged-in users receive AI vector neighbours
  when the product/index is available; the existing same-category list remains the frontend
  fallback while the AI index is warming up or unavailable.
- **Deferred capabilities:** collaborative-filtering recommendations (needs meaningful interaction
  volume), conversation summarization / long memory, and Anthropic/Voyage provider
  implementations (interfaces exist).
- **No global private buyer directory.** Seller recommendations use only the seller's authorized
  inbound leads. Published buying-intent projections omit owner ids and dedicated contact fields,
  but user-published free text cannot carry an absolute anonymity guarantee. Net-new buyer contact
  discovery requires a separate consent/contact-sharing domain workflow.
- **Pre-V7 multi-persona history is conservatively quarantined.** V7 records the exact live AI role
  set on every role-derived TOOL/ASSISTANT message. Older successful `get_lead` history has no way to
  prove whether it was buyer- or seller-authorized, so it requires both roles after upgrade rather
  than risking replay after role revocation. Start a fresh conversation if that legacy chat is hidden.
- **Compose DB-password default.** The compose blocks still default `AI_DB_PASSWORD` to a dev literal
  (loopback-bound ai-db mitigates exposure); operators MUST set a strong value — see `SECURITY-REVIEW.md`.
