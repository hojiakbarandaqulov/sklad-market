# AI Jobs discovery and invitation handoff

Branch: `feature/ai-chat-memory`. Additive Jobs source APIs only; existing role rules, ordinary search, contacts, uploaded files and candidate-initiated applications remain in place.

## Deployment

- Back up the Jobs database with the team's normal procedure. Preserve all environments, existing databases, uploads, users and volumes.
- Deploy this Jobs service before the AI service and clients. The existing `/api/v1/vacancies/**` and `/api/v1/me/resumes/**` gateway routes cover the new endpoints.
- The existing Jobs-specific Flyway configuration uses `db/skladjobs-migration`, `flyway_schema_history_sklad_jobs`, baseline version 0. V1 adds only `jobs_resume_discovery`, `jobs_ai_invitation` and their indexes. Existing resumes are not published or backfilled.
- On a nonempty legacy database Flyway baselines that service's history at 0 then applies V1. On an empty schema the additive tables are created first and existing Hibernate schema bootstrap creates base entities; FK additions are conditional to avoid failing before the existing bootstrap. Do not disable validation, reset Flyway history, or edit an applied migration.
- No migration was executed against a real PostgreSQL database during development. A staging migration and designated test-user smoke check are still required.

## Contracts (all use the existing ApiResponse `data` envelope)

Authenticated BUYER/SELLER only; new private responses use `Cache-Control: private, no-store`.

- `GET /api/v1/me/resumes/{id}/matching-profile`: owned resume only; resumeId, desiredPosition, skills, regionId, confirmed experienceLevel or null, salaryMin. Incomplete or oversized legacy professional fields require confirmation, not inferred or truncated data.
- `GET/PUT /api/v1/me/resumes/{id}/discovery`: owned resume only. Enabling requires `enabled:true`, `consentAccepted:true`, specialty (1–120), skills (1–20, each <=60), structured experienceLevel, and regionId matching the actual resume. Confirmed fields reject contacts/URLs/control characters. `{"enabled":false}` pauses discovery without repeating consent fields. Personal recommendations do not enable discovery.
- `GET /api/v1/vacancies/ai/owned`: up to 100 currently published, actually buyer/company-owned vacancies. No role-label-based ownership bypass.
- `GET /api/v1/vacancies/ai/candidates?vacancyId=&q=&page=1&perPage=5`: paginated, deterministic matching, page <=100/perPage <=20. The vacancy must be currently published and caller-owned. Current consent, owner, location, minimum experience and all occupation/specialization terms apply. Optional q only narrows professional skills/specialty; malformed or meaningless q is rejected. No whole-catalog fallback.
- Search results contain only id (resumeId), confirmed specialty/experienceLevel/regionId, bounded skills and evidence enums. One best eligible resume per candidate, sorted by requirements-skill evidence, experience fit and stable ID. Exact word matching distinguishes Java/JavaScript and React/React Native; bounded common EN/RU/UZ occupation aliases are deterministic, not an LLM classifier.
- `GET /api/v1/vacancies/ai/candidates/{resumeId}?vacancyId=`: fresh authorization/consent/eligibility checks. Adds optional professional workExperience, education and languages, each <=2000, stripped of known contact/name/file references and obvious email/phone/URL/handle/control strings. Never includes fullName, aboutMe, phone, email, attachments or fileId. These detail-only fields must not enter AI summaries or chat history.
- `POST /api/v1/vacancies/ai/invitations`: resumeId, vacancyId, message (optional <=1000), requestId (UUID). Ownership/live eligibility is rechecked under locks; no self-invitation. Same request/body is idempotent; changed body conflicts. A candidate can have only one pending invitation per vacancy, across all resumes. Existing applications cannot be invited again.
- `GET /api/v1/vacancies/ai/invitations?direction=incoming|outgoing&page=1&perPage=10`: account-scoped inbox/outbox; fresh consent masks withdrawn professional details, and lost vacancy authority masks outbox professional details. At most 20 rows/page. CLOSED/deleted/expired/withdrawn-profile invitations are presented as expired.
- `POST /api/v1/vacancies/ai/invitations/{id}/decline`: invited candidate only. `POST .../{id}/withdraw`: current actual vacancy owner only. No application or chat is created.
- Original application create request accepts optional `invitationId`. The existing BUYER-only explicit application and consent rules remain; invited user, exact resume, vacancy, pending/unexpired status, live discovery and inviter's current ownership are verified. Only a successful original application transaction marks ACCEPTED. Opening review is not acceptance.

## Bounds and limitations

- Invitations expire after 14 days. Per inviter/vacancy 5/minute and 30/day checks are serialized by the vacancy lock. Additional 60/day inviter-wide guard is best-effort across different vacancy transactions, not a claim of distributed global atomic rate limiting. Duplicate pending and request keys have database protections; no unbounded in-memory throttle map.
- Matching uses live database criteria, bounded page size and query term limits; no embeddings, model calls, background LLM tasks, conversation creation or contact enrichment. The public catalog remains independent of AI outages.
- Discovery withdrawal excludes fresh candidate search/detail reads. AI clients/history must rehydrate candidate references through authorized APIs, never persist professional resume snapshots.
- Legacy `/me/resumes/byId/{id}` now follows the existing owner/submitted-application policy instead of exposing arbitrary private resumes. Buyer-owned application resume access now uses actual vacancy ownership just like incoming applications; company permissions are not expanded.
- Staging should verify real PostgreSQL V1 application, existing Keycloak/gateway roles, owner transfer, discovery pause, closed vacancy, invite retry/concurrency, and explicit candidate submission with designated test records. Never send unsolicited production invitations for testing.
