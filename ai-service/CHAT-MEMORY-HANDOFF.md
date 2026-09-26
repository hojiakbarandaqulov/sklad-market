# AI chat memory update

Backend branch: `feature/ai-chat-memory`, based on team `upstream/main` at `f965ec8`.
Frontend counterpart: `sklad_market`, `feat/ai-agent`.

## Scope and behavior

- Only the AI service and frontend AI chat changed. No marketplace, orders, leads, catalog,
  recommendations, normal messaging or authentication services are migrated.
- Opening AI resumes the locally remembered chat, or the latest owner/role-accessible chat.
  New chat is explicit and does not save anything until the first message is sent.
- Creation supports an optional owner-scoped UUID `requestId`; retrying it returns the same session.
- Message requests support an optional UUID `requestId`. An accepted request is never executed
  twice under the same key. HTTP 409 `conversation_busy` means wait/reload; `request_recorded`
  means reload saved history. Existing clients without a key still get single-turn concurrency control.
- The frontend shows 15 sessions and 15 recent exchanges (question + answer). Pending draft
  confirmations are a bounded exception until expiry; they are not silently deleted.
- The backend archives older exchanges; model input uses at most 3 complete prior exchanges
  plus the current question. `AI_HISTORY_WINDOW_MESSAGES` can reduce that window.
- `AI_MAX_CONTEXT_BYTES` defaults to 131072. This is a UTF-8 serialized request-size budget,
  **not** an exact tokenizer count. It includes instructions, schemas, history and tool responses.
  Old complete exchanges are removed first; an oversized minimum request fails safely.
- At most 8 tool calls may execute within one chat turn. This does not change tool implementations.
- V13 is additive. Archived messages preserve role restrictions in `ai_conversation_access`.
  New history API: `GET /ai/conversations/latest`, `GET /ai/conversations/{id}/messages/recent`.
  Old paginated APIs remain available; frontend has a bounded old-server fallback.

## Safe rollout

1. Back up the existing AI database. Preserve existing databases, volumes and env files.
2. Deploy/restart **ai-service** with this branch so Flyway applies V13; then deploy the frontend.
   Do not copy these database changes into ordinary `chat-service`.
3. Check: open AI twice -> same chat; send two messages -> one conversation; New chat -> no
   new server conversation until sending; a second tab cannot generate a concurrent answer.
4. On a disposable PostgreSQL database, run:
   `./gradlew test --tests '*ChatMemoryIntegrationTest' --max-workers=1`
   This uses Testcontainers and is skipped when Docker is unavailable. Never use a production DB.
5. Permanent cleanup is **disabled by default**. After the database checks and backup, explicitly
   enable it with Spring property `ai.history.cleanup-enabled=true`
   (environment variable `AI_HISTORY_CLEANUP_ENABLED=true`). Preserve all other environment settings.

Cleanup runs hourly, processes at most 20 conversations / 1000 messages each, skips active turns
and unexpired pending drafts, and retains removed history for at least 30 days. Empty sessions
older than 24 hours are eligible. `ai.history.recovery-days` cannot be set below 30.
Tool audits and action drafts are preserved; message references in audits are detached safely.
Referenced conversations remain small tombstones. Orders/leads and buying intents are never deleted.
Archived data remains recoverable from the database during the grace period; there is no restore UI.
Disabling cleanup does not disable bounded model context, frontend history, or exchange archiving.

Before enabling cleanup, inspect archive counts and disk use. Retention is a storage policy, not
proof of a RAM leak; monitor JVM heap, active/queued requests and message/tool-payload sizes separately.
