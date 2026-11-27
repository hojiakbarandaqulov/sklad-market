# AI warehouse integration

Branch: `feature/ai-warehouse`, based on upstream `main` at `3feea655bc3da3b9c34149d9b48631316c89c6cc` (checked 2026-11-27).
This is a focused warehouse patch on the team's current platform, not a merge of the older
`feature/ai-chat-memory` branch. That branch and its pending Jobs work remain unchanged.

## Capabilities

- `get_my_warehouses`: the signed-in seller's warehouses, names, addresses and status.
- `search_my_inventory`: name/SKU search, warehouse, active/archive, on/off sale, and
  `ALL / LOW / OUT / AVAILABLE` stock filters. Distinct on-hand, reserved, available and minimum balances.
- `get_my_stock_operations`: recent movements, before/change/after quantities and recorded reasons;
  optional warehouse, inventory ID, operation type and ISO date range filters.
- Five records per tool request, explicit next-page requests only, compact projections and no shared data cache.
- Result kinds: `warehouse_list`, `inventory_search`, `stock_operations`. Cards link to existing
  `/warehouse` and `/history` pages. No invented detail routes. Plain-text presentation is supported.

## Verified contracts and access

The adapter uses only GET requests with the caller's Bearer token and platform language header:

1. `GET /api/v1/companies` resolves the caller's company. There is no model-supplied company ID.
2. `GET /api/v1/control/warehouses|products|operations` uses `companyId`, 1-based `page`,
   camelCase `perPage=5`, and the actual Control filter DTO fields.
3. Control retains its existing SELLER role, active-company and ownership checks. The AI registry
   AND the tool execution service reject guests, buyers, and admins without SELLER. Returned
   cross-company records, malformed envelopes/pages, timeouts and errors fail closed, not as zero stock.

No changes to Control, Jobs, catalog, orders, auth, gateway routes, database schemas or compose files.
No write client, stock adjustments, reservations, publication or automatic ordering is introduced.
Inventory descriptions and other arbitrary catalog fields are not copied into model context.
Returned names/addresses/reasons are bounded, authorized data, not model instructions.

## History and cost

Warehouse structured payloads are sent live but saved only as a small refresh/navigation marker.
Quantities, addresses and movement snapshots are not duplicated in tool history or audit records.
The assistant's textual answer still belongs to the user's normal protected chat history; it is not
a live inventory view. Follow-up stock questions must fetch again. Existing history/context limits,
quotas, provider selection, guest trials, catalog search and Jobs tools are unchanged.

## Deployment and smoke check

- Deploy/restart **ai-service from this branch**, with the team's existing current Control/company/gateway
  services and their working seller/company access. Do not replace the database or environment files.
- No new required environment variable; optional `AI_WAREHOUSE_TIMEOUT_MS` defaults to 3000, capped at 5000.
- Ship corresponding web/mobile `feat/ai-warehouse` builds for cards and seller suggestions.
  Older clients still receive the normal textual assistant answer.
- As an authorized seller, try “Which products are low in my warehouse?”, a specific SKU,
  “Show my warehouses”, and “Show recent stock movements”. Compare balances with Warehouse and history.
- Test archived and zero-stock filters, a missing catalog card, a next page, and explicit plain text.
- Reopen the chat: prior text remains; stock cards ask for a refresh. Verify a buyer/guest cannot read stock,
  another company is inaccessible, and an offline Control service produces a temporary error, not zero stock.

## Verification limits

The earlier local backend run compiled the warehouse tools and completed 123 checks: 119 passed,
four existing database-dependent guest integration checks skipped. It covered real HTTP fixtures,
JWT/language/query forwarding, role denial, schema validation, timeouts, malformed/cross-company
payloads, safe projections/history, and existing tool/Jobs/guest/chat-loop regression checks.
Small final projection/prompt/history refinements were source-reviewed; **Java was not rerun after
the user's explicit request to stop Java**. No live model, real seller account or production DB was used.
The deployment smoke check above is still required; local fixtures are not proof of deployment.
