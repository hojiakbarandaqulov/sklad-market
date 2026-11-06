# Order service-account: product API returns 403

The 2026-11-06 log shows `operation=product.order-summary upstream_status=403`.
Order is registered in Eureka (204) and discovers 15 instances. The failure is
authorization at the product internal endpoint, not order registration.

Product and Control order endpoints require the **realm role** `CONTROL_ORDER_WRITE`.
Both read roles from `realm_access.roles`. Assigning a client role with the same
name under `resource_access` does not meet this contract.

In Keycloak Admin Console, select `market-realm`:

1. In Realm roles, create `CONTROL_ORDER_WRITE` if absent.
2. Open Clients → the client configured as `ORDER_CLIENT_ID` (default `order-service`).
3. Ensure Client authentication and Service accounts roles are enabled.
4. In Service account roles → Assign role → Filter by realm roles, assign
   `CONTROL_ORDER_WRITE` to this service account only.
5. Ensure the client's role scope mappings allow this role and the `roles` default
   client scope maps realm roles into access tokens. If scope is restricted, add
   this single role to the dedicated scope. Do not grant admin roles or enable
   unrestricted scopes just to resolve this error.
6. Obtain a fresh client-credentials access token. Verify locally that
   `realm_access.roles` includes `CONTROL_ORDER_WRITE`. Never share the full token
   or client secret. The runtime token, not just the console assignment, is decisive.
7. Restart only order-service to clear its cached service token:
   `docker compose restart order-service`.
8. Retry the order with its original idempotency key and inspect fresh diagnostic
   logs. Product must allow the internal summary before Control reservation starts.

An upstream 403 is intentionally exposed as 503: the caller's buyer/seller token
did not fail this check; order's service account did. Keep internal role checks.
No changes to the database or Eureka addresses are required by this evidence.

For new application changes, deploy the updated code and use:
`docker compose up -d --build --no-deps --force-recreate order-service`.

Public order endpoints accept `Accept-Language: UZ`, `RU`, `EN` (default UZ).
Errors, validation and known reservation failure reasons follow that language.
Product names and user-entered notes/addresses are returned as stored.

Reference: https://www.keycloak.org/docs/latest/server_admin/#_service_accounts
