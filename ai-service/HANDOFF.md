# HANDOFF — ai-service deployment contract

This is the deployment contract with the backend team, who own `https://skladmarket.uz` and the
production `docker-compose.yml`. This file grows every phase and is finalized in Phase 7. AI
sessions build and verify locally (L1/L2); **the backend team deploys and runs L3** on the real
server with real users and real JWTs.

Local branch: `feature/ai-service` (created from `main`, never pushed). Everything in this
handoff describes what to do once that branch (or the relevant diff) reaches the server.

---

## 1. Env vars for the server's `.env`

All are additive — nothing here changes an existing variable. Placeholders only; real secrets
are the backend team's to generate/obtain.

```bash
# --- AI database (separate Postgres container, NOT the shared host Postgres on 5432) ---
AI_DB_USERNAME=ai_user
AI_DB_PASSWORD=REPLACE_WITH_STRONG_PASSWORD

# --- Gemini (server-side only; never in any frontend build) ---
GEMINI_API_KEY=REPLACE_ME                    # REQUIRED before Phase 1 chat works; not required for Phase 0
AI_CHAT_MODEL=gemini-2.5-flash               # re-check for a newer GA default at Phase 1 implementation time
AI_CHAT_MODEL_ADVANCED=gemini-2.5-pro
AI_EMBEDDING_MODEL=gemini-embedding-001
AI_EMBEDDING_DIM=768

# --- ai-service tuning (safe defaults already baked into docker-compose.yml; override only if needed) ---
AI_GATEWAY_BASE_URL=http://localhost:8080
AI_RATE_LIMIT_RPM=10
AI_DAILY_TOKEN_BUDGET=200000
AI_MAX_TOOL_ITERATIONS=6
AI_REQUEST_TIMEOUT_SECONDS=60
AI_DRAFT_TTL_MINUTES=30

# --- Phase 5 embedding indexer (all have safe in-app defaults; add to the compose ai-service block
#     ONLY if you want to override them — AI_EMBEDDING_MODEL/AI_EMBEDDING_DIM are already wired there) ---
AI_EMBEDDING_BATCH_SIZE=32                    # texts per Gemini batchEmbedContents call
AI_INDEXER_ENABLED=true                       # set false to disable the scheduled catalog indexer entirely
AI_INDEXER_INITIAL_DELAY_MS=60000             # first run this long after boot
AI_INDEXER_INTERVAL_MS=1800000                # then every 30 min (fixedDelay: after the previous run finishes)
AI_INDEXER_PAGE_SIZE=50                       # /api/v1/products/all page size while indexing
AI_INDEXER_MAX_PAGES=1000                     # hard cap on pages scanned per run (runaway guard)

# --- Phase 6 seller listing-assist (all have safe in-app defaults; add ONLY to override) ---
AI_SELLER_MAX_DESCRIPTION_CHARS=2000          # suggest-listing input cap (separate from chat's 4000)
AI_SELLER_MAX_IMAGES=4                        # max attachment ids per suggest-listing call
AI_SELLER_MAX_IMAGE_BYTES=6000000             # per-image size cap (~6MB) before it's skipped

# --- Phase 7 hardening / observability (safe in-app defaults; override ONLY if needed) ---
AI_CORS_ALLOWED_ORIGINS=                      # prod default empty = allow ONLY the service's own domain
                                              # (localhost origins are dev-only). Comma-separated list to widen.
LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs         # prod JSON log format (ecs|logstash|gelf). Empty/unset in a
                                              # non-prod profile = human-readable logs.
# NOTE (Phase 7 hardening): under the prod profile AI_DB_PASSWORD (or SPRING_DATASOURCE_PASSWORD, which the
# compose block supplies) is now REQUIRED — there is no baked-in fallback, so a missing value fails startup
# fast instead of silently using a guessable default. Always set a strong AI_DB_PASSWORD.
```

**Phase 6 also ships one small additive diff OUTSIDE `ai-service/`, in `category-service/`** — see
the note at the end of §2 below. It is a normal code change to an existing service the backend team
already owns and deploys; it just happens to have been authored in this session with the user's
explicit sign-off (PLAN.md §4.1 requires asking before touching another service, and the user chose
this over shipping seller listing-assist without real attribute-schema validation). Deploy it
exactly like any other category-service change — no new container, no new port, no schema migration
(it's a read-only endpoint over existing tables).

Notes:
- `AI_DB_USERNAME` / `AI_DB_PASSWORD` are consumed by **both** the `ai-db` container (as
  `POSTGRES_USER` / `POSTGRES_PASSWORD`) and `ai-service` (as `SPRING_DATASOURCE_USERNAME` /
  `SPRING_DATASOURCE_PASSWORD`, overriding the platform's shared `DB_USERNAME`/`DB_PASSWORD`
  that `x-common-env` would otherwise inject) — confirmed by `docker compose config` during
  Phase 0 verification (see §4).
- **Gemini key tier: still unset.** Phase 1 built and verified the full chat path against
  WireMock and a local boot, but no real `GEMINI_API_KEY` was available in this session — no
  live call to the Gemini API has been made yet. Whoever sets the real key in the server's `.env`
  must record its tier here **before** relying on Phase 1 in production: the free tier may be
  used by Google for prompt training (PLAN.md §2); production requires a paid-tier key. All
  Phase 1 env vars were already provisioned in `docker-compose.yml` by Phase 0 — nothing new to
  add here.

## 2. Deploy commands

```bash
cd backend
git fetch origin        # or however the branch reaches the server
git checkout feature/ai-service   # or merge per the team's normal review process — not our call
docker compose build ai-service
docker compose up -d ai-db ai-service
docker compose logs -f ai-service   # watch Flyway apply + startup
```

Rollback is additive-safe (§9 in root `PLAN.md`, restated in §5 below).

**Phase 6 category-service diff:** three files changed inside `category-service/src/main/java/org/example/`
(`controller/CategoryController.java`, `service/CategoryService.java`,
`service/impl/CategoryServiceImpl.java`) and one (`repository/CategoryAttributeRepository.java`)
gained a new derived-query method — all purely additive (no existing line touched), adding a single
new public endpoint `GET /api/v1/categories/{slug}/attributes` (`permitAll()`, same pattern as the
existing public category reads). No new dependency, no schema change, no new bean wiring beyond the
existing `CategoryAttributeRepository` being injected into `CategoryServiceImpl` (that class already
used `@RequiredArgsConstructor`, so the new constructor param is automatic). Rebuild/redeploy
category-service the same way the team normally does; there is no separate container or port for
this. Verified locally: `cd backend/category-service && ./gradlew compileJava` — clean.

## 3. Post-deploy verification checklist (cumulative — grows every phase)

### Phase 0

```bash
# 1. Health is up (not gateway-routed — /actuator/health isn't under /api/v1/ai/**, check container-local)
docker compose exec ai-service curl -s http://127.0.0.1:8091/actuator/health
# expected: {"status":"UP"}

# 2. ping is reachable through the gateway and requires auth
curl -s -o /dev/null -w "%{http_code}\n" https://skladmarket.uz/api/v1/ai/ping
# expected: 401

# 3. ping with a real user token (obtain via the normal login flow)
curl -s -H "Authorization: Bearer <REAL_JWT>" https://skladmarket.uz/api/v1/ai/ping
# expected: 200, body: {"success":true,"data":{"service":"ai-service","status":"ok","time":"..."}}

# 4. Flyway applied cleanly (no manual SQL needed)
docker compose exec ai-db psql -U ${AI_DB_USERNAME:-ai_user} -d skalad_market_ai \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
# expected: one row, version 1, success = t
```

### Phase 1

Uses the same `V1` schema Phase 0 already migrated (`conversation`, `message`, `usage_ledger`) —
no new Flyway version. Replace `<TOKEN>` with a real user JWT obtained via the normal login flow.

```bash
# 1. Conversation endpoints require auth
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://skladmarket.uz/api/v1/ai/conversations
# expected: 401

# 2. Create a conversation
curl -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{}' https://skladmarket.uz/api/v1/ai/conversations
# expected: 200, {"success":true,"data":{"id":"<uuid>","title":null,"locale":"ru",...}}
# save the returned id as $CID

# 3. List conversations — the one from step 2 must appear
curl -s -H "Authorization: Bearer <TOKEN>" "https://skladmarket.uz/api/v1/ai/conversations?page=1&per_page=20"

# 4. Stream a chat turn (SSE) — -N disables curl's own buffering
curl -N -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"Привет! Что ты умеешь?"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: text/event-stream body; response headers include
#   X-Accel-Buffering: no
#   Cache-Control: no-cache
# event sequence: one or more `event: token` frames with incremental {"text":"..."},
# then `event: usage` {"tokensIn":n,"tokensOut":n,"budgetRemaining":n},
# then `event: done` {"messageId":"...","conversationId":"..."}.
# The reply must be in Russian (locale sent above) and must say it cannot browse the
# catalog yet if asked to search/recommend real products.

# 5. Ownership isolation — a DIFFERENT user's token against the same $CID
curl -s -H "Authorization: Bearer <OTHER_USER_TOKEN>" \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: 404, {"success":false,"message":"Conversation not found"} (not another user's data)

# 6. Messages persisted from step 4
curl -s -H "Authorization: Bearer <TOKEN>" \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: 200, paged list with the user message then the assistant reply, in order

# 7. Guardrail: exceed AI_RATE_LIMIT_RPM within a minute against the same $CID
# expected: the (RPM+1)th call's SSE stream emits `event: error`
#   {"code":"rate_limited","message":"..."} instead of tokens, then the stream ends (HTTP 200)

# 8. Guardrail: send a message longer than 4000 characters
# expected: `event: error` {"code":"invalid_input",...}, no provider call made

# 9. Soft delete
curl -s -X DELETE -H "Authorization: Bearer <TOKEN>" \
  https://skladmarket.uz/api/v1/ai/conversations/$CID
# expected: 200, {"success":true}; the conversation must no longer appear in step 3's listing
```

If no `GEMINI_API_KEY` is configured yet, steps 4/7/8 will instead return `event: error`
`{"code":"provider_error","message":"The AI service is temporarily unavailable."}` for every
turn — that is expected until the key is set (§1 above).

### Phase 2

New Flyway `V2__tool_audit.sql` (`tool_audit` table) — verify it applied alongside `V1` (step 4
below). No new `/api/v1/ai/**` routes: tools are invoked internally by the existing SSE endpoint,
role-filtered per caller. Replace `<TOKEN>` with a real user JWT; `$CID` from a fresh
`POST /api/v1/ai/conversations` (Phase 1 step 2).

```bash
# 1. Grounded search — the model must call search_products before answering
curl -N -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"найди оптовый рис в Ташкенте дешевле 10000 сум"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected event sequence: `event: tool_start` {"tool":"search_products","summary":"..."},
# `event: tool_end` {"tool":"search_products","status":"ok"}, then `event: token` frames citing
# real product names + slugs (or, honestly, "no results" if the catalog has none — verified empty
# on this environment as of 2026-07-08), then `event: usage`, then `event: done`. The reply must
# NOT say "I cannot browse the catalog" (that was Phase 1's honest limitation, now resolved).

# 2. Tool failure is reported, not swallowed — ask about a category that doesn't exist
curl -N -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"покажи товары в категории \"zzz-no-such-category\""}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: `event: tool_start`/`event: tool_end` (status "error") for search_products, then a
# reply that plainly says the category wasn't found — never a fabricated product list.

# 3. Iteration cap doesn't hang the turn — a query likely to prompt repeated tool use still
# completes within AI_REQUEST_TIMEOUT_SECONDS and ends in `event: done` (not a stalled stream).

# 4. Flyway V2 applied cleanly alongside V1
docker compose exec ai-db psql -U ${AI_DB_USERNAME:-ai_user} -d skalad_market_ai \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
# expected: two rows — version 1 (init) and version 2 (tool audit), both success = t

# 5. Every tool call from step 1/2 produced an audit row
docker compose exec ai-db psql -U ${AI_DB_USERNAME:-ai_user} -d skalad_market_ai \
  -c "select tool_name, result_status, http_status, latency_ms from tool_audit order by created_at desc limit 5;"
# expected: one row per tool_start/tool_end pair above, result_status 'ok' or 'error' matching the
# SSE status, http_status/latency_ms populated

# 6. Role-filtered allowlist — confirm no privileged tool leaks to a plain BUYER token (Phase 2
# ships only role-open tools, so this is a placeholder for Phase 6's seller/admin toolsets: revisit
# once role-gated tools exist)
```

**Known local-environment caveat (2026-07-08):** the catalog/category/company data on
`https://skladmarket.uz` was empty at verification time (0 categories, 0 products) — grounded
search transcripts will show "no results" rather than real product citations until the platform
has live data. The tool contracts themselves were verified against the real, empty responses
(200 with empty arrays, not errors) plus WireMock-based unit tests mirroring populated responses.

### Phase 3 (frontend — `skladx-market-source-2`, local branch `feature/ai-agent`)

The frontend's `/ai-agent` page is flag-gated (`VITE_FEATURE_AI_AGENT`) and was built/verified
entirely against a **stubbed** SSE client (vitest, 41 tests) since ai-service isn't deployed yet.
Once this branch (or its diff) is live behind `https://skladmarket.uz`, run this browser check:

```text
1. Set VITE_FEATURE_AI_AGENT=true in skladx-market-source-2/.env.local, npm run dev.
2. Log in as a real buyer/seller account, open /ai-agent.
3. Expected: the greeting + suggestion chips render (src/ai/i18n, `ru` default); send a message.
4. Expected event flow visible in the UI: the user bubble appears immediately, the assistant
   bubble streams tokens in (no full-page reload, no CORS/proxy error in devtools), a
   tool_start/tool_end chip appears if the model calls a catalog tool (e.g. "Ищу по каталогу…"),
   and the turn ends cleanly (input re-enables).
5. Expected on failure instead: a localized ErrorCard (rate_limited / budget_exceeded /
   provider_error / timeout / invalid_input / unauthenticated / network per PLAN.md §6) — never a
   blank page or an unhandled exception in the console.
6. Confirm the `Accept-Language` header sent by the browser matches the RU/UZ/EN switcher on the
   page (Network tab, the POST .../messages request) and that ai-service's stored
   conversation.locale reflects it.
```

Local-only verification performed this session (no live ai-service to test against): flag off
renders today's exact mock UI (byte-for-byte, screenshotted); flag on + logged out shows a
localized "log in first" prompt linking to `/login` (screenshotted); flag on + logged in was
exercised via vitest against a stubbed `streamAiMessage` (token streaming, tool status chips,
typed error card, retry-last) rather than a live browser session, since doing so against the real
`https://skladmarket.uz` would have required real user credentials this session didn't have —
production login was intentionally not attempted with guessed/fake credentials.

### Phase 4 (action drafts + confirm flow + digest)

New Flyway `V3__action_draft.sql` (`action_draft` table) — verify it applied alongside `V1`/`V2`
(step in the checklist below). New routes: `GET /api/v1/ai/drafts/{id}`,
`POST /api/v1/ai/drafts/{id}/confirm`, `POST /api/v1/ai/drafts/{id}/cancel` — all under the existing
`/api/v1/ai/**` gateway route, no
gateway/compose edits needed. New env var `AI_DRAFT_TTL_MINUTES` (default 30, already provisioned
in `docker-compose.yml`/§1 above by an earlier session). Replace `<TOKEN>` with a real **BUYER**
JWT; `$CID` from a fresh `POST /api/v1/ai/conversations`.

```bash
# 1. "20 мешков цемента" scenario: draft -> confirm -> real lead visible in the buyer's leads
curl -N -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" \
  -d '{"content":"Хочу заявку на 20 мешков цемента М500 (cement-m500), меня зовут Али, телефон +998901234567"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected event sequence includes `event: tool_start` {"tool":"draft_lead",...},
# `event: tool_end` {"tool":"draft_lead","status":"ok"}, then `event: draft`
# {"draftId":"<uuid>","type":"LEAD","payload":{...,"items":[...],"companyName":"...",...}}
# save the draftId as $DRAFT

# 2. Confirm — executes the real POST /api/v1/leads with THIS request's own token
curl -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{}' \
  https://skladmarket.uz/api/v1/ai/drafts/$DRAFT/confirm
# expected: 200, {"success":true,"data":{"draftId":"...","status":"CONFIRMED","leadId":<id>}}

# 3. The confirmed lead is now visible via the normal buyer leads endpoint
curl -s -H "Authorization: Bearer <TOKEN>" https://skladmarket.uz/api/v1/leads
# expected: includes the lead id from step 2

# 4. Idempotent double-confirm — repeat step 2 with the SAME $DRAFT
curl -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{}' \
  https://skladmarket.uz/api/v1/ai/drafts/$DRAFT/confirm
# expected: 200, same leadId as step 2, AND no second lead created (still one row in step 3's list)

# 5. Cancel — draft a second lead, then cancel it instead of confirming
curl -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{}' \
  https://skladmarket.uz/api/v1/ai/drafts/$DRAFT2/cancel
# expected: 200, {"success":true,"data":{"draftId":"...","status":"CANCELLED"}}; confirming it
# afterward must 409, and no lead is created for it

# 6. Expiry — a draft older than AI_DRAFT_TTL_MINUTES cannot be confirmed
# expected: confirm on an expired draft -> 409 Conflict, {"success":false,"message":"...expired..."}

# 7. Foreign-user 404 — a DIFFERENT buyer's token against $DRAFT
curl -s -X POST -H "Authorization: Bearer <OTHER_BUYER_TOKEN>" -H "Content-Type: application/json" \
  -d '{}' https://skladmarket.uz/api/v1/ai/drafts/$DRAFT/confirm
# expected: 404 (not another buyer's draft, not a 403 that would leak existence)

# 8. Digest — "what's happening with my requests?" after step 2
curl -N -s -X POST -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"Что у меня с заявками и корзиной?"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: tool_start/tool_end for get_my_leads (and get_cart/get_my_favorites/get_unread_chats
# as relevant), then a plain-text summary citing the real lead from step 2 by status — never an
# invented lead.

# 9. Flyway V3 applied cleanly alongside V1/V2
docker compose exec ai-db psql -U ${AI_DB_USERNAME:-ai_user} -d skalad_market_ai \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
# expected: three rows — versions 1, 2, 3, all success = t
```

**Frontend (`skladx-market-source-2`, local branch `feature/ai-agent`):** once ai-service is
deployed, repeat the Phase 3 browser checklist above but type the cement request from step 1;
expected additionally: a `DraftLeadCard` renders inline in the assistant's reply (itemized
products, pre-filled editable contact fields, Confirm/Cancel buttons); pressing Confirm shows a
localized success line with the real lead id; pressing Cancel shows a localized cancelled line;
neither button double-submits (both disable while the request is in flight).

**Known frontend gap, not a regression:** PLAN.md's "success links to the user's leads view"
assumed a buyer-facing leads/requests page exists in `skladx-market-source-2` — it does not (only
a seller-side `RequestsTab` exists, for leads received, not sent). `DraftLeadCard`'s confirmed
state therefore shows the lead id inline with no link, and the AI feature does not add a new route
(forbidden by PLAN.md §4.1 — `src/App.jsx` is off-limits). Whoever builds a buyer leads page later
can extend `DraftLeadCard`'s confirmed branch with a real link in one place.

### Phase 5 (embeddings index, semantic search, similar products)

```bash
# 1. Flyway V4 applied cleanly alongside V1-V3
docker compose exec ai-db psql -U ${AI_DB_USERNAME:-ai_user} -d skalad_market_ai \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
# expected: four rows — versions 1, 2, 3, 4 (product embedding), all success = t

# 2. The vector table + HNSW cosine index exist
docker compose exec ai-db psql -U ${AI_DB_USERNAME:-ai_user} -d skalad_market_ai \
  -c "select indexname, indexdef from pg_indexes where tablename='product_embedding';"
# expected: idx_product_embedding_hnsw USING hnsw (embedding vector_cosine_ops), plus pkey + category index

# 3. Admin authz matrix on the reindex endpoints (obtain tokens via the normal login flow)
curl -si -X POST https://skladmarket.uz/api/v1/ai/admin/reindex ; echo               # 401 (no token)
curl -si -X POST -H "Authorization: Bearer $BUYER_TOKEN"  https://skladmarket.uz/api/v1/ai/admin/reindex ; echo   # 403
curl -si -X POST -H "Authorization: Bearer $ADMIN_TOKEN"  https://skladmarket.uz/api/v1/ai/admin/reindex ; echo   # 200 {"success":true,"data":{"started":true,...}}

# 4. Watch the reindex run + check status (admin token). GEMINI_API_KEY must be set for embeddings.
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" https://skladmarket.uz/api/v1/ai/admin/reindex/status | jq
# expected: {"running":false|true,"indexSize":N,"lastStatus":"SUCCESS|PARTIAL|FAILURE","productsIndexed":N,"notes":"trigger=admin pages=.. visible=.. embedded=.. ..."}
# Verify only APPROVED+active products are counted (indexSize should match the public catalog's approved count, not /all's total).

# 5. Semantic search (authenticated) — cross-lingual: a Russian query should return Uzbek-titled products
curl -s -H "Authorization: Bearer $USER_TOKEN" "https://skladmarket.uz/api/v1/ai/search?q=%D1%80%D0%B8%D1%81%20%D0%BE%D0%BF%D1%82%D0%BE%D0%BC&limit=5" | jq
# expected: {"success":true,"data":{"query":"рис оптом","count":N,"items":[{"slug":"...","name":"...","score":0.x},...]}} ranked by cosine

# 6. Similar products (authenticated) — pass a real APPROVED product id
curl -s -H "Authorization: Bearer $USER_TOKEN" "https://skladmarket.uz/api/v1/ai/similar/123?limit=6" | jq
# expected: {"success":true,"data":{"productId":123,"count":N,"items":[...]}}; a product id NOT in the index -> 404

# 7. Failure isolation (independent domain): stop product-service (or point the indexer at a dead gateway),
#    trigger a reindex, confirm chat is UNAFFECTED and an index_state FAILURE row is written, no crash-loop.
docker compose exec ai-db psql -U ${AI_DB_USERNAME:-ai_user} -d skalad_market_ai \
  -c "select id,last_status,products_indexed,left(notes,80) from index_state order by id desc limit 3;"
# expected: a FAILURE row; meanwhile POST /api/v1/ai/conversations/{id}/messages still streams normally.

# 8. Agent tools: ask the agent a conceptual/cross-lingual product question and confirm it calls
#    semantic_search_products (tool_start/tool_end SSE), and "show me products like <slug>" triggers
#    find_similar_products — both citing real slugs, never invented.
```

**"Similar products" is a consumable API for the frontend team** (PLAN.md §8): `GET /api/v1/ai/similar/{productId}`
returns ranked neighbours; wiring it into `ProductPage` is their call — ai-service does not touch their frontend.

### Phase 6 (seller & admin assist — role-gated toolsets)

No new Flyway migration (nothing here is persisted). New routes: `POST /api/v1/ai/seller/suggest-listing`
(`hasRole('SELLER')`, plain JSON not SSE). New seller/admin tools flow through the EXISTING chat
endpoint, gated per-turn by the caller's live JWT roles (not a fixed route). Also requires the
additive category-service diff above to be deployed (`GET /api/v1/categories/{slug}/attributes`).
Replace `<TOKEN>` with a real JWT of the indicated role; `$CID` from a fresh
`POST /api/v1/ai/conversations`.

```bash
# 1. Category attribute schema (category-service, additive, public) — pick any real category slug
curl -s "https://skladmarket.uz/api/v1/categories/<slug>/attributes" | jq
# expected: 200, {"success":true,"data":[{"id":..,"code":..,"label":..,"dataType":"TEXT|NUMBER|BOOLEAN|SELECT",
#   "isRequired":..,"isFilterable":..,"optionsJson":..,"sortOrder":..}, ...]} (empty array if the
# category has no attributes configured yet — not an error)

# 2. Seller suggest-listing authz matrix
curl -si -X POST -H "Content-Type: application/json" -d '{"description":"cement"}' \
  https://skladmarket.uz/api/v1/ai/seller/suggest-listing ; echo                        # 401 (no token)
curl -si -X POST -H "Authorization: Bearer $BUYER_TOKEN" -H "Content-Type: application/json" \
  -d '{"description":"cement"}' https://skladmarket.uz/api/v1/ai/seller/suggest-listing ; echo   # 403
curl -si -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"description":"cement"}' https://skladmarket.uz/api/v1/ai/seller/suggest-listing ; echo   # 403 (ADMIN is a different persona, not "any elevated role")
curl -s -X POST -H "Authorization: Bearer $SELLER_TOKEN" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"description":"Цемент М500 в мешках по 50 кг, оптом от 1 тонны"}' \
  https://skladmarket.uz/api/v1/ai/seller/suggest-listing | jq
# expected: 200, {"success":true,"data":{"category":{"slug":..,"name":..}|null,"categoryConfidence":0.x,
#   "attributes":[{"code":..,"label":..,"dataType":..,"value":..}, ...],"missingRequired":[...],"notes":..}}
# Every attribute in "attributes" must be a real code from step 1's schema for that category, with a
# value that's actually valid for its dataType (a NUMBER field never has a non-numeric value; a
# SELECT field's value is always a real optionsJson member) — the strict-validation contract.

# 3. Seller suggest-listing WITH photos (attach ids from the seller's own prior uploads via the
#    existing app upload flow, POST /api/v1/attach/upload — reuse any real product photo id)
curl -s -X POST -H "Authorization: Bearer $SELLER_TOKEN" -H "Content-Type: application/json" \
  -d '{"description":"cement bags","imageIds":["<real-attach-id>"]}' \
  https://skladmarket.uz/api/v1/ai/seller/suggest-listing | jq
# expected: 200; response quality should visibly reflect the photo (e.g. more confident category)

# 4. Seller chat persona — role-gated tools reachable ONLY via chat, in the buyer's/seller's own language
curl -N -s -X POST -H "Authorization: Bearer $SELLER_TOKEN" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"Есть ли у меня новые заявки от покупателей?"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: `event: tool_start` {"tool":"get_seller_leads",...} -> `tool_end` (status "ok") -> a
# plain-text summary citing real leads (or honestly "no leads" if none exist)

curl -N -s -X POST -H "Authorization: Bearer $SELLER_TOKEN" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"Напиши черновик ответа на первую заявку"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: `event: tool_start` {"tool":"draft_lead_reply",...} -> `tool_end` -> the assistant's
# reply presents the draft text CLEARLY labeled as a draft the seller must send themselves —
# never phrased as already sent. No lead/chat state changes anywhere on the platform.

# 5. BUYER token never reaches seller/admin tools (registry gate proven live, not just in tests)
curl -N -s -X POST -H "Authorization: Bearer $BUYER_TOKEN" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"Покажи очередь модерации"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: NO `tool_start` for get_moderation_queue/get_reports/summarize_moderation_item/
# get_seller_leads/draft_lead_reply/draft_chat_reply anywhere in the stream — the assistant should
# just explain (briefly) that it can't do that for this account, never fabricate a queue.

# 6. Admin chat persona — triage summaries, never a decision
curl -N -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"Покажи очередь модерации"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: `tool_start`/`tool_end` for get_moderation_queue, then a summary citing real pending
# products/companies by id/name — never claims anything was approved/rejected.

curl -N -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -H "Accept-Language: RU" -d '{"content":"Есть новые жалобы на товары?"}' \
  https://skladmarket.uz/api/v1/ai/conversations/$CID/messages
# expected: `tool_start`/`tool_end` for get_reports, reply cites the REAL reasonCode values
# (SAME/FAKE/OFFENSIVE/DUPLICATE/SCAM) verbatim — never an invented reason.

# 7. Grep proof (repeat on the deployed checkout): no seller/admin tool calls a write endpoint
grep -rniE '\.post\(|\.put\(|\.delete\(|\.patch\(' \
  ai-service/src/main/java/org/example/ai/tool/impl/GetSellerLeadsTool.java \
  ai-service/src/main/java/org/example/ai/tool/impl/DraftLeadReplyTool.java \
  ai-service/src/main/java/org/example/ai/tool/impl/DraftChatReplyTool.java \
  ai-service/src/main/java/org/example/ai/tool/impl/GetModerationQueueTool.java \
  ai-service/src/main/java/org/example/ai/tool/impl/GetReportsTool.java \
  ai-service/src/main/java/org/example/ai/tool/impl/SummarizeModerationItemTool.java
# expected: only Map.put(...) matches (building local response maps) — zero real HTTP write calls;
# GatewayClient itself only exposes get/getBytes (see GatewayClientHasNoWriteMethodsTest)
```

**Known scope note:** `summarize_moderation_item` for `targetType=COMPANY` only works if the
company is still in the current moderation queue — company-service has no per-id admin detail
endpoint on this platform (verified in source), so the tool searches the unpaged queue list rather
than guessing at a nonexistent endpoint; it returns an honest explanatory error otherwise, never
invented data.

**Indexer notes for the operator:** the scheduled indexer starts `AI_INDEXER_INITIAL_DELAY_MS` after boot and
repeats every `AI_INDEXER_INTERVAL_MS` (fixed delay after completion, so runs never overlap themselves); a
manual `POST /api/v1/ai/admin/reindex` runs off the same dedicated single-thread pool and is skipped (not queued)
if one is already running. It reads **only public endpoints** (`/api/v1/products/all`, `/api/v1/categories`) with
**no credentials**, so it needs no service account. Only `status=="APPROVED" && isActive==true` products are
indexed (mirrors the catalog's own visibility rule; a soft-deleted-but-APPROVED product is inactive and excluded);
products that lose visibility or disappear have their vector rows deleted on the next run. All indexing/embedding
runs on `aiIndexerScheduler`, never a chat thread — an indexer failure cannot degrade chat.

### Phase 7 (hardening, observability, resilience)

```bash
# 1. Actuator: health is public; metrics + prometheus are AUTH-GATED (require a valid JWT).
curl -s https://skladmarket.uz/api/v1/ai/../actuator/health   # container-local: curl :8091/actuator/health
# expected: {"status":"UP"} (public)
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8091/actuator/prometheus
# expected: 401 unauthenticated
curl -s -H "Authorization: Bearer $ADMIN_JWT" http://localhost:8091/actuator/prometheus | grep -E 'ai_chat_turn_duration|ai_tool_call_duration|ai_provider_stream_duration|ai_indexer_embeddings|http_server_requests'
# expected: the AI custom meters + http.server.requests are present (200 with a token)
# NOTE: /actuator/env, /heapdump, /beans, /loggers etc. are NOT exposed at all — only health,info,metrics,prometheus.

# 2. Structured JSON logs in prod. Each line is ECS JSON and carries the request correlation id.
docker compose logs --since=2m ai-service | tail -1
# expected: a single-line JSON object. Every request also echoes an X-Request-Id response header:
curl -s -D - -o /dev/null http://localhost:8091/actuator/health | grep -i x-request-id
# expected: X-Request-Id: <uuid or the sanitized inbound value>

# 3. Degradation matrix (each yields a typed error / DOWN, never a crash or internal leak):
#  a) ai-db down:  docker compose stop ai-db  -> /actuator/health = 503 {"status":"DOWN"}, ping still 401 (process alive)
#  b) provider down (unset GEMINI_API_KEY or a bad key): a chat turn ends with `event: error`
#     {"code":"provider_error",...}; JSON search -> 503 {"success":false,"message":"..."}; NO stacktrace to the client
#  c) gateway/product-service down: grounded tools return an honest "temporarily unavailable" in the reply;
#     the indexer writes a FAILURE index_state row and chat is unaffected (independent failure domain)
#  d) budget exhausted (per-user daily): next turn -> `event: error` {"code":"budget_exceeded",...} before any provider call
#  e) rate limited: (RPM+1)th turn/min -> `event: error` {"code":"rate_limited",...}; AI_RATE_LIMIT_RPM=0 blocks all

# 4. Eval harness (deterministic, offline) is green in CI:
cd backend/ai-service && ./gradlew eval
# expected: GoldenSetEvalTest 28/28 (100%). Optional live model eval: AI_EVAL_LIVE=true GEMINI_API_KEY=... ./gradlew eval
```

**Gemini key tier (finalize before go-live):** record the tier of the key set in the server `.env`
here. Production **requires a paid-tier key** — on the free tier Google may use prompts for training
(PLAN.md §2/T3). A live embedding demo was run in Phase 5 with the user's own key; the deployed key
is the operator's to set and record.

---

## 4. Observability & cost

- **Metrics (Micrometer, auth-gated `/actuator/prometheus` + `/actuator/metrics`):**
  - `http.server.requests` — request counts + latency + status per endpoint (auto).
  - `ai.chat.turn.duration{outcome}` — chat-turn latency tagged success / rate_limited / budget_exceeded /
    invalid_input / provider_error / timeout.
  - `ai.chat.tokens.in` / `ai.chat.tokens.out` — token usage distribution per completed turn.
  - `ai.tool.call.duration{tool,status}` — per-tool latency + error rate (status ok|error).
  - `ai.provider.stream.duration{status}` — Gemini streaming latency per round.
  - `ai.indexer.running` (0/1) and `ai.indexer.embeddings` (row count) — indexer stats.
  - Plus JVM / HikariCP / system metrics from the actuator defaults. Scrape with an admin/service JWT.
- **Structured logging:** the prod profile emits ECS JSON to stdout (`logging.structured.format.console=ecs`;
  Docker/journald captures it). `RequestIdFilter` stamps a sanitized correlation id into the MDC
  (`requestId`) and the `X-Request-Id` response header on every request, so logs and responses correlate.
  Message bodies are never logged; tokens/keys are never logged.
- **Cost query (authoritative token ledger, `usage_ledger`).** Tokens are recorded per user per day.
  Adjust the per-1M rates to your Gemini contract (flash vs pro):

  ```sql
  -- Daily total tokens + estimated USD cost across all users
  SELECT day,
         SUM(tokens_in)     AS tokens_in,
         SUM(tokens_out)    AS tokens_out,
         SUM(request_count) AS requests,
         ROUND(SUM(tokens_in)  / 1000000.0 * :in_rate_per_million
             + SUM(tokens_out) / 1000000.0 * :out_rate_per_million, 4) AS est_cost_usd
  FROM usage_ledger
  GROUP BY day
  ORDER BY day DESC;

  -- Top spenders for a given day (spot heavy users / abuse)
  SELECT user_sub, tokens_in, tokens_out, request_count
  FROM usage_ledger
  WHERE day = CURRENT_DATE
  ORDER BY (tokens_in + tokens_out) DESC
  LIMIT 20;
  ```

  `AI_DAILY_TOKEN_BUDGET` (per user, in+out) caps this; lower it to tighten spend, `AI_RATE_LIMIT_RPM=0`
  to freeze chat entirely.

---

## 5. Rollback / kill switches (mirrors root `PLAN.md` §9)

| Level | Action | Effect |
|-------|--------|--------|
| Instant | `docker compose stop ai-service` | Gateway route 503s; rest of the platform unaffected |
| Config | `AI_RATE_LIMIT_RPM=0` + restart `ai-service` | Hard-disables chat while keeping health/read endpoints up (from Phase 1 on) |
| Config | `AI_INDEXER_ENABLED=false` + restart `ai-service` | Disables the scheduled catalog indexer only; existing `product_embedding` rows keep serving search/similar (from Phase 5 on) |
| Full | Remove the two additive blocks in `docker-compose.yml` (`ai-db`, `ai-service`, the `ai-db-data` volume line) and the additive route block in `api-gateway/src/main/resources/application.yml`; `docker compose down ai-service ai-db` | Byte-identical restoration of the pre-project state |

---

## Phase log

- **Phase 0**: service skeleton, JWT security baseline, Flyway V1
  (`vector` extension + `conversation`/`message`/`usage_ledger`, created early so Phase 1 starts
  on real DDL), `GET /api/v1/ai/ping`, additive gateway route, additive `ai-db` + `ai-service`
  compose blocks. No Gemini integration yet — `GEMINI_API_KEY` is provisioned in compose but
  unused until Phase 1.
- **Phase 1**: `ChatModelProvider` + `GeminiChatModelProvider` (`com.google.genai:google-genai`
  1.60.0 — verified: default 5-attempt retry disabled via `HttpRetryOptions.attempts(1)`, our own
  single retry with jitter on the initial call only, typed error mapping preserving the real
  HTTP status); conversation/message/usage_ledger persistence owner-scoped by JWT `sub`
  (`POST/GET /api/v1/ai/conversations`, `GET .../{id}/messages`, `DELETE .../{id}`); the SSE chat
  endpoint `POST /api/v1/ai/conversations/{id}/messages` with the token/usage/done/error protocol,
  nginx-safe headers, 15s heartbeats, and disconnect cancellation; pre-provider guardrails (Caffeine
  RPM limiter, daily token budget, 4000-char input cap, 20-message history window) that always
  resolve to a typed `error` SSE event, never a raw 500; system prompt v1
  (`prompts/system-agent.md`, language-adaptive uz/ru/en, Russian default, explicitly states it
  cannot browse the catalog yet). No new Flyway migration — Phase 0's `V1` schema already matched.
  No new env vars — everything Phase 1 needs was already provisioned in `docker-compose.yml` by
  Phase 0. **Not yet exercised: a real Gemini call** — no `GEMINI_API_KEY` was available in this
  session; the provider logic is verified against WireMock instead (see PLAN.md Progress Log).
- **Phase 2**: manual Gemini function-calling loop (`ChatModelProvider` extended with vendor-neutral
  `ToolSpec`/`ToolCallRequest`/`ToolCallOutcome`/`ToolExchangeEntry`; `GeminiChatModelProvider` is
  the only class touching `com.google.genai.types.Tool/FunctionDeclaration/FunctionCall/
  FunctionResponse` — confirmed via decompiling the SDK's own `Models`/`AfcUtil` classes that the
  function-call turn keeps role `"model"` and the function-response turn uses role `"user"`, there
  being no separate `"function"` role in this API); `AgentTool`/`ToolRegistry` (role-filtered
  allowlist) + `ToolArgumentValidator` (schema/enum-checked before any HTTP call, PLAN.md §4.2 item
  5); `GatewayClient` (`RestClient`, GET-only, user-JWT pass-through, every 4xx mapped uniformly to
  "not found" without parsing the body); five read-only tools (`search_products`, `get_product`,
  `list_categories`, `get_catalog_filters`, `get_company`) with compact, size-capped projections;
  `tool_audit` (Flyway `V2`) + `ToolAuditService` — one row per execution; `tool_start`/`tool_end`
  SSE events; `UntrustedDataWrapper` + system prompt v2 (`prompts/system-agent-v2.md`) for the
  PLAN.md §4.2 item 4 injection defense (unit test + a WireMock-Gemini test asserting a malicious
  string reaches the model only inside a `functionResponse` JSON field, verbatim, never merged into
  free text). `AiChatController`/`AiChatService`/`AiSecurityUtil` gained a `bearerToken` pass-through
  (captured on the request thread — `aiChatExecutor` doesn't propagate `SecurityContext` — same
  pattern as the existing `userSub` capture) so tools can forward the caller's own JWT; caller roles
  for tool filtering currently come from `conversation.user_role` (a single primary-role snapshot
  taken at conversation creation, per Phase 1) since none of Phase 2's tools are role-gated —
  **Phase 6 (seller/admin toolsets) will need live per-request roles**, following the same capture
  pattern used for `bearerToken`, if it needs precise multi-role filtering. New env vars consumed
  for the first time (already provisioned in `docker-compose.yml`/HANDOFF.md by Phase 0):
  `AI_GATEWAY_BASE_URL`, `AI_MAX_TOOL_ITERATIONS` (default 6). No compose/gateway edits needed.
  **Drift re-verified live against `https://skladmarket.uz` on 2026-07-08** (see tool javadocs for
  the per-endpoint detail): §7 item 7 (category is a numeric id, not a slug) — still true; item 8
  (no minPrice/maxPrice/saleType params) — still true, so these unsupported filters are no longer
  advertised or simulated over one page; item 9's *list* half
  (categories list doesn't filter `isActive`) — still true, filtered in `list_categories`; item 9's
  *by-slug* half is **outdated** — an unknown/inactive category slug now returns **HTTP 400** with
  a plain-text body (`"source cannot be null"`, a `ModelMapper.map(null, ...)` `IllegalArgumentException`)
  instead of the old `success:true, data:null`; item 10 (4xx never 404) — still true, but the body
  shape differs per service (product-service: JSON `{success:false,message,errors,trace_id}`;
  company-service: plain text) — `GatewayClient` never parses the body, so this doesn't matter;
  item 11 changed on current main: the public company DTO now also includes business phones,
  website, and establishment date; `get_company` exposes only an explicit public-field allowlist.
  The live catalog/category/company data was empty at
  verification time (see §3 Phase 2 caveat above) — tool *contracts* were verified against the
  real (empty) responses; populated-response behavior is covered by WireMock unit tests instead.
- **Phase 3** (frontend, separate `skladx-market-source-2` repo, local branch `feature/ai-agent`):
  flag-gated `/ai-agent` rewrite — `src/ai/**` (i18n with `ru`/`uz`/`en` dictionaries and a
  completeness test, a chunk-safe SSE parser, `useAiChat` reducer, hand-rolled markdown with no
  raw HTML and internal-links-only `/product`/`/company` anchors, chat UI components), additive
  vitest setup (41 tests green), `VITE_FEATURE_AI_AGENT` flag + `.env.example`. Verified field-for-
  field against this repo's actual DTOs before wiring: `SendMessageRequest.content` (not `text`),
  `ConversationController`'s `per_page` query param (not `perPage`), and every SSE payload shape in
  `SseEventPublisher`/`AiErrorCode` (`token.text`, `tool_start.{tool,summary}`,
  `tool_end.{tool,status}`, `usage.{tokensIn,tokensOut,budgetRemaining}`,
  `done.{messageId,conversationId}`, `error.{code,message}` with the five wire codes). No backend
  changes. See the Phase 3 L3 checklist above for what to run once ai-service is deployed.
- **Phase 4** (action drafts + confirm flow + digest): Flyway `V3__action_draft.sql`
  (`action_draft`: id/conversation_id/user_sub/type/payload jsonb/status/idempotency_key
  unique/lead_id/created_at/confirmed_at/expires_at) + `ActionDraft` entity/repository. Verified in
  `lead-service` source before wiring anything: `LeadController.create()` is `hasRole('BUYER')`
  and `LeadCreateRequest` is exactly `{source, productId, productIds, quantity (single, applied to
  ALL items — §7 item 14 confirmed unchanged), contactName, contactPhone, contactEmail,
  deliveryAddress, neededDate, comment}`; `LeadServiceImpl.create()` attributes the WHOLE lead to
  the FIRST resolved product's seller/company with no per-item splitting, so `draft_lead`
  (`ai/tool/impl/DraftLeadTool`) requires every requested product to share one company (verified
  via each product's public detail) and rejects mixed-seller requests with a clear error rather
  than silently misattributing items. `draft_lead` never calls lead-service: it re-verifies every
  slug via `GET /api/v1/products/slug/{slug}` (existence + `APPROVED` status + company id — T8), then
  persists an `action_draft` row and returns `draftId`/`draftType`/`draftPayload` (a richer display
  shape — item names/prices/company — that's a strict superset of the stored `LeadCreateRequest`
  fields). Four new read-only digest tools, each contract verified in source before wiring:
  `get_my_leads` (`GET /api/v1/leads`, `hasRole('BUYER')`, params `status?`/`page`/`perPage` — NOT
  `per_page`), `get_lead` (`GET /api/v1/leads/{id}`, `hasAnyRole('BUYER','SELLER')`), `get_cart`
  (`GET /api/v1/cart`, class-level `hasRole('BUYER')`), `get_my_favorites`
  (`GET /api/v1/product-favorites`, no `@PreAuthorize` — any authenticated caller, page params
  `page`/`perPage`, response is Jackson's default `PageImpl` shape — reused `RemoteSpringPage`),
  `get_unread_chats` (`GET /api/v1/chats`, no `@PreAuthorize`, page param is `per_page` — unlike
  the lead/favorite endpoints — filters to threads with `unread_count > 0` client-side).
  **The one and only class that calls `POST /api/v1/leads`** is the new
  `ActionDraftConfirmService` — grep-verified (`grep -rn '"/api/v1/leads"' src/main` shows it as
  the sole `.post()` call site; every other reference is a `GET`) — it deliberately owns a private
  `RestClient` instead of the tool-facing `GatewayClient` (which stays structurally GET-only, so no
  tool can ever gain write access even by mistake). Confirm re-loads the draft by
  id+`user_sub` under a pessimistic database write lock (foreign user -> 404 via the existing
  `AiNotFoundException`), lazily flips
  DRAFT -> EXPIRED on read once `AI_DRAFT_TTL_MINUTES` has elapsed, is idempotent (a second confirm
  on an already-CONFIRMED draft returns the same `leadId` without a second lead-service call —
  verified via WireMock request-count assertions; concurrent confirms in the same AI database are
  serialized by the row lock), applies optional buyer-edited contact-field
  overrides (`DraftConfirmRequest`, "editable contacts" in the card) while re-validating
  `contactName`/`contactPhone` are still non-blank, and forwards the confirming request's OWN
  bearer token (not the one captured when the draft was created). Because lead-service has no
  idempotency-key contract, a crash after its POST succeeds but before the AI transaction commits
  remains a documented manual-reconciliation edge case. `POST/POST
  /api/v1/ai/drafts/{id}/confirm|cancel` (`AiDraftController`), 409 Conflict for
  expired/cancelled/rejected-by-lead-service via a new `ActionDraftStateException` handler in
  `GlobalExceptionHandler`. `draft` SSE event wired into `AiChatServiceImpl.executeToolCall`: any
  successful tool result carrying `draftId`/`draftType`/`draftPayload` keys triggers
  `SseEventPublisher.sendDraft` (currently only `draft_lead` produces those keys) — exact wire
  shape is sent live, while the TOOL history stores only an opaque `draftRef`. Owner-scoped
  `GET /api/v1/ai/drafts/{id}` returns current status and private payload only while still DRAFT, so
  reload can restore Confirm/Cancel without resurrecting a confirmed/cancelled/expired snapshot.
  shape verified via a new `AiChatControllerTest` case asserting the raw SSE body. System prompt v3
  (`prompts/system-agent-v3.md`) adds drafting/digest policy (call `draft_lead` proactively since a
  human always approves after; never claim a request was "sent", only "drafted"; one seller per
  call). `ToolExecutionContext` gained a `conversationId` field (needed to scope the persisted
  draft to its conversation) — additive, all 8 existing construction sites updated. New env var
  `AI_DRAFT_TTL_MINUTES` (default 30) added to `application.yml`; it was already present in
  `docker-compose.yml`/§1 above from an earlier pass, so no compose edit was needed this phase. L1:
  103 tests green (up from 62; WireMock field-for-field verification of the outgoing
  `LeadCreateRequest` body against the real DTO via `equalToJson(..., ignoreExtraElements=false)`,
  double-confirm idempotency, expiry, cancel, foreign-user 404, multi-seller rejection,
  unapproved-product rejection, digest projections for all four new tools). L2: real boot against
  local `ai-db` — Flyway `V3` applied cleanly alongside `V1`/`V2`, full context (all new beans)
  constructed without error, health UP, both new `/api/v1/ai/drafts/**` endpoints 401
  unauthenticated. No live Gemini/gateway call made this session (no `GEMINI_API_KEY` available) —
  see the Phase 4 L3 checklist above for the post-deploy script. **Frontend drift found**: PLAN.md
  assumed a buyer-facing "leads view" exists to link to on confirm success — it doesn't in this
  frontend codebase (only a seller-side `RequestsTab` for received leads) — documented above rather
  than worked around by adding a new route (forbidden by PLAN.md §4.1).
- **Phase 5** (embeddings index, semantic search, similar products): Flyway `V4__product_embedding.sql`
  (`product_embedding` with `embedding vector(768)` + HNSW `vector_cosine_ops` index + a category
  btree index; `index_state` run log). `EmbeddingProvider` + `GeminiEmbeddingProvider`
  (`com.google.genai` 1.60.0 `Models.embedContent(model, List<String>, EmbedContentConfig)` → single
  `:batchEmbedContents` call; `taskType` RETRIEVAL_DOCUMENT/QUERY, `outputDimensionality(768)`;
  `response.embeddings()`→`ContentEmbedding.values()`; **MRL-truncated vectors are L2-renormalized** —
  read from the actual SDK classes, retry-off + own single-retry mirroring the chat provider).
  Vector persistence/query is **plain JDBC** (`ProductEmbeddingRepository`): `CAST(? AS vector)`
  bound text literals (`VectorLiterals`, `Float.toString`, never concatenated — §7 item 5), `<=>`
  cosine ranking, `1 - (embedding <=> q)` similarity score, same-category boost in the "similar"
  ORDER BY. **Content-hash indexer** (`ProductIndexer`): paginates the PUBLIC `GET /api/v1/products/all`
  via a new credential-free `PublicCatalogClient` (deliberately separate from the user-JWT
  `GatewayClient`); **excludes unapproved/inactive/deleted products** by indexing only
  `status=="APPROVED" && isActive==true` (verified in source + live 2026-07-11 that `/all` = plain
  `findAll` with NO moderation/active/soft-delete filter, §7 item 12; its `ProductResponse` items DO
  expose `status`/`isActive`/`description`/`attributes`/`categoryId`, so no per-product detail fetch
  is needed — the one field detail adds, category NAME, is resolved once per run from a cached
  `/api/v1/categories` map); embeds `name + category + short/long description + flattened attributes`;
  **skips unchanged rows by SHA-256 content hash**; deletes rows for products that lost visibility or
  disappeared. Runs on its own `aiIndexerScheduler` (first `@EnableScheduling` in ai-service, via
  `IndexerScheduleConfig implements SchedulingConfigurer` — never a chat thread, §7 item 6) with an
  `AtomicBoolean` overlap guard; **every downstream failure is caught and recorded as an `index_state`
  row, never thrown** (independent failure domain — proven at L2, below). Scheduled + admin-triggered
  (`POST /api/v1/ai/admin/reindex`) + status (`GET /api/v1/ai/admin/reindex/status`), both
  `@PreAuthorize hasAnyRole('ADMIN','SUPER_ADMIN')` (dual-gated with SecurityConfig; 401/403/200
  matrix in `AiAdminControllerTest`). Read endpoints `GET /api/v1/ai/search?q=&limit=` +
  `GET /api/v1/ai/similar/{productId}?limit=` (authenticated, query the local vector table only —
  no downstream call). Two new agent tools `semantic_search_products`(query) +
  `find_similar_products`(slug, resolved server-side) + system prompt **v4** (`system-agent-v4.md`:
  keyword-first, semantic when weak/cross-lingual). **Latent bug fixed:** the admin endpoints are the
  first `@PreAuthorize` routes in ai-service; `GlobalExceptionHandler`'s catch-all would have turned
  `AuthorizationDeniedException` into a **500 instead of 403** — added an `AccessDeniedException`→403
  handler (also maps `AiChatException` codes to HTTP for the JSON search endpoint). New env
  (all default-safe, HANDOFF §1): `AI_EMBEDDING_BATCH_SIZE`, `AI_INDEXER_ENABLED`,
  `AI_INDEXER_INITIAL_DELAY_MS`, `AI_INDEXER_INTERVAL_MS`, `AI_INDEXER_PAGE_SIZE`, `AI_INDEXER_MAX_PAGES`
  — no compose edit (Phase 0 already wired `AI_EMBEDDING_MODEL`/`AI_EMBEDDING_DIM`). **L1: 152 tests
  green** (up from 103), incl. **7 real pgvector Testcontainers tests** (`pgvector/pgvector:pg16`,
  Docker was available) exercising the actual `<=>` cosine ranking / upsert / category-boost / delete;
  WireMock tests for the Gemini batch-embed API and for the indexer's exclusion/hash-skip/removal/
  failure-isolation/overlap-guard. **L2: real boot against local `ai-db`** — `V4` applied cleanly on
  top of `V1`-`V3`, `idx_product_embedding_hnsw` (`hnsw ... vector_cosine_ops`) present, health UP,
  all `/api/v1/ai/**` (incl. search/similar/admin) 401 unauthenticated; **failure-isolation
  demonstrated live** — with the gateway unreachable the scheduled indexer ran on its own
  `ai-indexer-1` thread, wrote a `FAILURE` `index_state` row (`products_indexed=0`), did not crash or
  loop, and chat/health/all endpoints stayed fully responsive. See the Phase 5 L3 checklist above for
  the post-deploy admin-reindex + cross-lingual search + similar demonstrations.
- **Phase 6** (seller & admin assist, role-gated toolsets): **fixed a latent gating gap flagged by
  Phase 2's own notes** — tool-registry role gating previously read `conversation.userRole`, a
  single-role snapshot (`roles.split(",")[0]`, alphabetically first) taken once at conversation
  creation; a caller holding two roles (e.g. SELLER+BUYER) or one who gains a role mid-conversation
  was gated on stale/wrong data. `AiSecurityUtil.currentRoleSet()` now captures the FULL live role
  set on the request thread (same capture-before-async-dispatch pattern as `bearerToken`/`userSub`)
  and `AiChatController` threads it through `AiChatService.streamMessage(..., callerRoles)` into the
  turn loop, which uses it for both tool-registry filtering and system-prompt persona selection —
  `Conversation.userRole` now stores the compact creation-time AI-role snapshot and is rechecked for
  history/replay; V7 per-message provenance additionally captures roles gained later and protects
  role-derived assistant summaries after revocation.
  **Persona-aware system prompt v5** (`system-agent-v5.md`, content unchanged from v4) +
  `persona-seller-v1.md`/`persona-admin-v1.md` fragments appended by `SystemPromptProvider.render(locale,
  callerRoles)` only when the caller actually holds that role — a BUYER-only caller is never even told
  the seller/admin tools exist. **Six new tools**, all wired into the EXISTING chat/tool loop (no new
  SSE event types): `get_seller_leads` (`GET /api/v1/leads/seller`, `SELLER`), `draft_lead_reply` +
  `draft_chat_reply` (`SELLER`; each re-fetches real lead/chat data via `GatewayClient` then makes ONE
  extra non-streaming `ChatModelProvider.generate()` call — the interface's own javadoc already called
  this out as its intended future use — to produce draft TEXT ONLY, never sent by any code path),
  `get_moderation_queue` + `get_reports` + `summarize_moderation_item` (`ADMIN`/`SUPER_ADMIN`). The
  existing `get_lead` tool (Phase 4, already `BUYER`+`SELLER`-gated) covers "get lead details" for
  sellers too — no duplicate tool needed. **Verified-in-source drift from PLAN.md's Phase 6 spec:**
  the `ReasonCode` enum it expected on product/company-service's own moderation flow actually lives in
  **report-service**, on user-submitted complaints against already-live items (`SAME|FAKE|OFFENSIVE|
  DUPLICATE|SCAM`) — product/company moderation of NEW submissions uses a free-text reason instead.
  Added `get_reports` (`GET /api/v1/admin/reports`) to surface the real enum honestly rather than
  force-fitting it onto the wrong flow; `get_moderation_queue` covers the new-submission queues PLAN.md
  actually meant. **C8 seller listing-assist** (`POST /api/v1/ai/seller/suggest-listing`, plain JSON,
  not SSE — a one-shot suggestion, not a chat turn): two `ChatModelProvider.generateStructured()` calls
  (new vision-capable, JSON-schema-constrained, non-streaming provider method — `GenerateContentConfig
  .responseMimeType("application/json")` + `.responseSchema(Schema.fromJson(...))`, images via
  `Part.fromBytes(bytes, mimeType)`, verified against the real 1.60.0 SDK classes) on the advanced
  model: (1) pick a category from the REAL active category list (JSON-schema `enum` constrains the
  model to real slugs; the result is STILL re-verified server-side against the same list — a
  hallucinated slug returns `category:null`, never a fabricated match); (2) extract attribute values
  against that category's REAL schema. **Closed a genuine platform gap, with the user's explicit
  sign-off** (asked first per PLAN.md §4.1, since it required touching another service): category-service
  had NO read endpoint for `CategoryAttribute` anywhere — attribute CRUD was admin-only and write-only
  (POST/PUT/DELETE, no GET), and even the frontend's own `AddProductModal` doesn't render per-category
  attribute fields. Added one new public, `permitAll()` endpoint mirroring the existing category-read
  pattern exactly: `GET /api/v1/categories/{slug}/attributes` (see the diff note in §2 above) — purely
  additive, zero existing lines touched, `./gradlew compileJava` verified clean. Every model-proposed
  attribute value is STRICTLY validated server-side (`CategoryAttributeSchema`) against the real
  `DataType` (`TEXT|NUMBER|BOOLEAN|SELECT` — confirmed no `STRING`/`DATE` exist) and, for `SELECT`,
  real `optionsJson` membership; anything that fails validation is DROPPED (never passed through), and
  a dropped REQUIRED field surfaces in `missingRequired` rather than silently vanishing.
  `optionsJson` has zero enforced shape anywhere on the platform (opaque `TEXT` column, no parsing
  logic in category-service itself) — `CategoryAttributeSchema.parseOptions` best-effort-handles a
  plain string array or an array of `{value,label}`-ish objects and degrades to "no enum constraint"
  otherwise, documented as a platform convention gap rather than a real contract. `GatewayClient`
  gained `getBytes()` (raw-bytes GET, for file-service's public `GET /api/v1/attach/open/{id}` —
  verified: public/`permitAll`, no ownership check on the platform side, string key ids from the
  upload response) — still structurally GET-only, so no tool gained any write capability.
  **Dual-layer role enforcement, proven two ways**: `SellerAdminToolRoleGatingTest` constructs the
  REAL `ToolRegistry` with the REAL Phase 6 tool instances and proves a BUYER-only role set reaches
  none of the six new tools (plus a multi-role regression case: SELLER+ADMIN together must see BOTH
  toolsets — this specifically guards the live-roles fix above); `AiSellerControllerTest` proves
  `@PreAuthorize("hasRole('SELLER')")` independently (401/403-BUYER/403-ADMIN/200-SELLER). **Grep +
  structural proof of no seller/admin writes**: `GatewayClientHasNoWriteMethodsTest` reflects over
  `GatewayClient`'s public methods and fails if anything but a `get`-prefixed method ever appears;
  `grep -rniE '\.post\(|\.put\(|\.delete\(|\.patch\('` over all six new tool files matches only
  `Map.put(...)` (building local response maps) — zero real HTTP write calls (see the exact command in
  the Phase 6 L3 checklist above). **Frontend** (`skladx-market-source-2`, `feature/ai-agent`): entirely
  inside `src/ai/**` + `AiAgentPage.jsx`, no new runtime deps. `SellerListingHelper` (paste description +
  optional photos, uploaded via the platform's own `POST /api/v1/attach/upload`) + `SuggestedListingCard`
  (per-field copy-to-clipboard, no auto-fill of the team's add-product form — out of scope per §4.1);
  toggled by a `role === "SELLER"` entry button next to the language switcher. `Suggestions` is now
  role-aware (`role` prop adds seller/admin-flavored chips on top of the base set) — admin's "surface"
  is plain chat responses from the new tools, no dedicated admin widget, matching PLAN.md's literal ask.
  Six new tool names added to `ToolStatusChip`'s known-tools set + all three i18n dictionaries (ru/uz/en,
  completeness test is key-set-generic so it needed no code change, just complete additions). **L1: 203
  backend tests green** (up from 154) + **70 frontend tests green** (up from 53) — `npm run lint` clean
  on the entire `src/ai/**` surface (pre-existing lint errors in the frontend team's own dirty files,
  e.g. `ProductPage.jsx`/`ProfilePage.jsx`/`CatalogPage.jsx`, are unrelated and untouched — confirmed by
  grep, zero hits for `src/ai` or `AiAgentPage` in the lint output), `npm run build` green. **L2: real
  boot against local `ai-db`** — no new Flyway version needed (Phase 6 persists nothing), full context
  (all new beans incl. the two new tools' `ChatModelProvider`/`GatewayClient` dependencies) constructed
  without error, health UP, all `/api/v1/ai/**` endpoints incl. the new
  `POST /api/v1/ai/seller/suggest-listing` 401 unauthenticated. `category-service` compiles clean
  standalone (`./gradlew compileJava`); its own local DB boot wasn't attempted this session (out of
  scope for an ai-service-focused L2, and the new endpoint is a straightforward read mirroring an
  already-proven-live pattern). See the Phase 6 L3 checklist above for the full post-deploy script
  (category attribute schema read, seller authz matrix, vision-assisted suggestion, seller/admin chat
  personas, and the live BUYER-exclusion check).
- **Phase 7** (hardening, observability, evals, docs — this handoff finalized): full security audit
  of §4.2 items 1–10 + §5 T1–T10 against the code (`SECURITY-REVIEW.md`, all PASS; `npm audit` 0
  vulns, backend deps current + pinned, secret scan clean). Two in-allowlist prod-hardening fixes:
  **CORS prod-lock** (`ai.cors.allowed-origins`; prod allows only `server.domain`, localhost is
  dev-only) and **DB-password fail-fast** (`application-prod.yml` no longer defaults
  `AI_DB_PASSWORD`). **Observability:** `micrometer-registry-prometheus` + auth-gated
  `/actuator/metrics` + `/actuator/prometheus` (health-only stays public); custom meters
  `ai.chat.turn.duration{outcome}`, `ai.chat.tokens.in|out`, `ai.tool.call.duration{tool,status}`,
  `ai.provider.stream.duration{status}`, `ai.indexer.running|embeddings`; prod ECS structured JSON
  logging (`logging.structured.format.console`, Spring Boot 3.4 native, no new dep) + a
  `RequestIdFilter` that stamps a sanitized `requestId` MDC + `X-Request-Id` header; documented
  `usage_ledger` cost query (§4 above). **Eval harness:** `evals/golden-set.json` (28 cases) +
  `GoldenSetEvalTest` (deterministic, 28/28, wired into `test` and `./gradlew eval`, threshold 100%)
  + optional env-gated `LiveToolSelectionEvalIT` (live model judgment, threshold 85%). **Resilience
  matrix verified** on a real local boot: ai-db down → health 503 / process alive; provider down →
  typed `provider_error`; gateway down → honest tool errors + isolated indexer FAILURE; budget/rate
  → typed SSE errors. **L1: 209 tests green** (2 pgvector Testcontainers + 1 live-eval gated-skip, 0
  failures). **L2: real boot vs local ai-db** — Flyway V1–V4 applied, all new observability beans
  wired, health UP, `/actuator/prometheus` + `/metrics` + `/api/v1/ai/**` 401 unauthenticated,
  `X-Request-Id` stamped, DB-down → health 503 with the process still serving. New env vars (all
  default-safe, §1): `AI_CORS_ALLOWED_ORIGINS`, `LOGGING_STRUCTURED_FORMAT_CONSOLE`. No new
  compose/gateway edits. Change surface stays within PLAN.md §4.1 (`ai-service/**` only this phase).
- **Phase 8** (business discovery + privacy-safe matchmaking, local feature branch): Flyway `V5`
  adds `company_embedding` (public company name plus verification status and approved/active catalog capabilities,
  `vector(768)`, HNSW/category/region indexes) and an isolated index run ledger. Complete-crawl proof
  is required before stale deletion; capped/incomplete crawls retain prior rows, embedding batch
  cardinality is validated before upsert, and admin triggers reserve atomically. Public contacts are
  not embedded/indexed and are stripped from persisted structured-result snapshots; top company
  results hydrate only company-service's public phone/site/address. Owner-visible assistant prose can
  still mention a live contact result and must not be treated as a current directory snapshot.
  New authenticated APIs: `GET /api/v1/ai/business-search`, BUYER-only
  `POST /api/v1/ai/recommendations/suppliers`, and admin-only business reindex/status. New tools:
  `search_businesses` (role-open), `recommend_suppliers` (BUYER), and `recommend_buyers` (SELLER;
  caller-scoped `/api/v1/leads/seller` only). Scores expose grounded reason codes and non-guarantee
  language; no tool performs outreach. Flyway `V6` adds opt-in buying intents whose account/contact
  columns are excluded from seller projections (`DRAFT -> PUBLISHED -> CLOSED/EXPIRED`): BUYER
  owner APIs/tools and SELLER search whose projections
  omit owner ids and dedicated contact fields. Buyer-authored free text is screened but must still
  be reviewed before publication. SSE adds `result_set` for typed frontend cards
  while retaining assistant prose/backward-compatible payloads. Audit persistence redacts free-text
  and PII-capable tool arguments before storage. Search fuses semantic and local lexical candidates,
  survives embedding-provider failure, and reports index `asOf`/staleness. Public-contact hydration
  is capped, parallel, optional, and uses a separate short-timeout client. Supplier-wide price
  filtering/ranges are omitted because catalogs can mix currencies. Buying-intent publication
  requires `{ "publicationConsent": true }`; owner history is paginated, active rows are quota-capped,
  and expiry/retention maintenance is bounded. See `application.yml` for the
  `AI_BUSINESS_INDEX_*`, `AI_BUSINESS_CONTACT_*`, and `AI_BUYING_INTENT_*` tunables.
  Flyway `V7` adds compact per-message AI-role provenance. History and model replay re-authorize the
  creation snapshot, exact V7 turn roles, and successful legacy tool names; failed hallucinated tool
  calls never become access requirements. Pre-V7 successful multi-persona `get_lead` history is
  intentionally conservative and requires both BUYER and SELLER because its original persona cannot
  be inferred safely.

  **Final local verification:** backend inventory is 328 tests (all 325 runnable tests green; 3
  live-provider gated skips). The full invocation exposed one Mockito-only test assertion error; it
  was corrected and passed in isolation. Real Docker PostgreSQL/pgvector migrated V1–V7 and passed
  persistence/repository coverage. Frontend completed 125/125 AI vitest tests, AI/shared-auth
  ESLint, and the 3,610-module production build.
