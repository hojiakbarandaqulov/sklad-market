# AI Jobs integration (AI branches only)

## Deploy in this order

1. Back up the existing databases. Keep all current `.env`, application secrets, databases and uploaded files. Do not reset schemas, drop data or replace Keycloak/users.
2. Deploy the updated `sklad-jobs` service. Its additive public `GET /api/v1/vacancies/ai-candidates` adapter supplies live published/nondeleted vacancies without incrementing views. Existing gateway `/api/v1/vacancies/**` routing already forwards it; no gateway permissions/routes were changed.
3. Deploy `ai-service`, allowing its normal Flyway startup to apply **V15__ai_job_preferences.sql** to the existing AI database. It creates only `ai_job_preferences`; existing history, guest counters, resumes and Jobs tables stay untouched. Do not edit earlier migrations or manually clear Flyway history.
4. Deploy the AI web/mobile branch changes. Keep `AI_GATEWAY_BASE_URL` pointing to the platform gateway. Optional new `AI_JOBS_TIMEOUT_MS` defaults to 3000 (clamped to 100–5000 ms). No new model, embedding, background indexing or Jobs API credential is required. Existing frontend AI feature flags and guest trial configuration remain in force.

## Behavior and security

- Any registered role may explicitly save one private job preferences profile; identity always comes from the authenticated JWT `sub`, never a submitted user ID. `GET/PUT/DELETE /api/v1/ai/jobs/preferences` reads/upserts/deletes only that row. GET responses use `Cache-Control: no-store`.
- Desired occupation, 1–20 skills, actual positive region and explicit experience level are required. Real Jobs category, minimum UZS amount, employment type and schedule are optional. Category/region selection is validated through their actual public platform APIs. No contact fields, full resumes or private employer comments are imported or persisted.
- `GET /api/v1/ai/jobs/categories` returns the real Jobs taxonomy (max 500), not product categories. `GET /api/v1/ai/jobs/recommendations?limit=5` is deterministic: **no model/embedding call, no conversation/session creation and no token/LLM quota charge**. Categories, preference saves and recommendations have isolated 20/minute request guards.
- `search_jobs` is a read-only authenticated agent tool, also allowed in the existing guest trial. `recommend_jobs` uses only the signed-in caller's preferences and returns `job_profile_required` when setup is missing. Guests cannot access saved preferences, recommendations or resumes; existing total trial limits/idempotency continue unchanged.
- Matching evaluates at most 40 targeted live candidates, deduplicates and revalidates at most six selected IDs against the database. Chat defaults to three cards; Jobs defaults to five. Saved region, category, minimum salary, schedule and employment type are constraints by default; explicit tool filters override them. Saved experience excludes jobs that demand more experience. No-match remains a no-match; it never expands to unrelated catalog results. Service failures are surfaced separately.
- EN/RU/UZ occupation aliases and public skill evidence are used without per-page model costs. This is bounded lexical/occupation matching, not a guarantee of hiring or exhaustive cross-language semantic search. Only compact grounded cards enter existing bounded chat/history. Descriptions/requirements are used briefly in matching, not returned to the model; no fabricated percentage/period/contact details.
- Optional company enrichment uses a **separate** `aiJobsCompanySummary` Feign child context: 250 ms connection, 300 ms read, no retries, 1.2 s request enrichment budget, stop after first outage. DB read transaction ends before HTTP enrichment. Missing company names/logos stay missing for client fallbacks. Ordinary Jobs Feign behavior is unchanged.
- View/Apply links use server-created numeric `/jobs/{id}` paths. Existing application flow still requires **BUYER**, an owned resume, review, consent and explicit submit. AI tools do not apply, edit resumes, assign roles or message employers. A job may close after a card was displayed; the existing application service validates publication again when the user submits.

## Focused local verification

- `ai-service`: compile plus 27 focused tests passed across targeted runs for matching/multilingual relevance, specialization/skill evidence, hard defaults, owner scoping/roles, guest allowlist, credential-free fixed-path client, unavailable vs empty, live shortlist revalidation, malformed-input 400s and existing account chat/tool loop. Final matching/controller/guest edits were checked again before handoff.
- `sklad-jobs`: compile and focused public repository/controller tests passed using an isolated in-memory H2 database, never the user's/server database. Covers live status/deletion, apostrophes, no view increments and preserved ordinary public routes.
- No live provider request, production application, real PostgreSQL V15 migration, deploy or phone/device test was performed here. A normal staged backend-first rollout and smoke test is still necessary.

## Short smoke check

1. With a registered account, save desired occupation, genuine skills, region and experience. Reload web/mobile; verify the same single profile is restored.
2. Open Jobs recommendations. Confirm only relevant published jobs satisfy the saved constraints; lower-than-minimum salary and unrelated occupations must not appear. Ask the chat for the same occupation; verify up to three clickable cards and grounded reasons.
3. Edit or clear preferences. Verify subsequent recommendations reflect the change or show setup. A second account must never see the first account's profile.
4. As guest, search public jobs within the normal trial. Saved setup/recommendations/applications must require sign-in; exhausting the trial still produces the existing registration/login CTA.
5. Open a job with a BUYER account, select an owned resume and review the existing consent/application UI. Submit only to a designated test vacancy with explicit permission. Other roles get browsing, not a bypass.
6. Close a test vacancy through existing Jobs controls; rerun AI search. It must disappear. Stop Jobs temporarily: ordinary frontend Jobs/AI error handling should remain usable, and AI must say unavailable rather than inventing results. Stop company service only: valid job cards should remain with honest missing-logo/name fallbacks.

Work remains on `feature/ai-chat-memory`. Publish only this AI branch; do not merge it into main or deploy it as part of publishing. Backend-first staged deployment and the real-data smoke check above remain separate steps.
