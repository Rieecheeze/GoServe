# eGovPay adapter contract

The adapter requires `integrationEnabled: true` and `demoOnly: true`. The checkout workflow uses
the existing private configuration and Vault key. The additive migration and Edge functions are
deployed and enabled for supervised tests. A complete app-bound sandbox transaction still requires participant onboarding and a staff-configured service.
Tests use synthetic credentials and records. Real-money payment remains disabled.

## Verified provider contract

The [current official catalog](https://platforms.e.gov.ph/api-catalogs/egovpay) documents:

| Operation | Route | Successful response |
| --- | --- | --- |
| Create payment | `POST {base_url}/api/v1/transaction` | HTTP 201, `data.uuid` and `data.url`; current example also includes `data.channel.refno` |
| Check transaction | `GET {base_url}/api/v1/transaction/{uuid}` | HTTP 200, transaction details including `environment_type` and `payment_status` |

Both routes use `X-eGovPay-Token`. Requests use `application/json; charset=utf-8`.
Required creation fields are `amount`, `items` with `name` and `amount`, `settlement_template_uuid`,
`redirect_url`, `callback_url`, `txnid`, and `digest`. Currency is optional; this adapter sends `PHP`.
The digest is HMAC-SHA256 over `amount + "|" + txnid`, keyed by the issued merchant key.

A live sandbox check on 22 October 2026 verified that `test_` decorates the request header;
HMAC uses the undecorated issued key. Signing with the decorated header token returned HTTP
422 for `digest`. Using the issued key returned HTTP 201 and a checkout visibly marked Test
Mode. An authenticated details lookup returned `environment_type: TEST`, `INITIAL`, PHP and
the expected amount. A separate `1.00` spelling check failed digest validation; the adapter now
strips insignificant zeros before signing and sending, without floating-point conversion or
changing monetary value. The saved Vault key is unchanged.

On 23 October 2026, after explicit approval of the marked Test Mode checkout, an authenticated
provider GET returned `environment_type: TEST`, `payment_status: PAID`, a nonempty `paid_at`,
and the exact original UUID, txnid, amount `1.0000`, and PHP currency. This diagnostic checkout
was not bound to an application payment and must never credit one. Only `INITIAL` and `PAID`
have verified mappings. `PAID` also requires `paid_at`; `FAILED`, `CANCELLED`, `SUCCESS`, and
every other unsupported status stay unknown.

The [supplied developer page](https://egovpay.gov.ph/developers) is a public JavaScript application.
Its linked developer definitions use JSON strings for amounts and show `TEST` with `INITIAL` in
the status example. Its checkout response omits the channel reference. The current catalog labels
amounts as doubles and mixes numeric request examples with string amounts in saved requests.
The adapter preserves positive decimal values, emits lowercase hexadecimal HMAC, and
does not convert units, round amounts, or calculate fees.

The [portal overview](https://platforms.e.gov.ph/) identifies its services as staging.
The catalog deliberately omits the base URL because it is issued with credentials. The exact
credential-issued URL has now returned a verified test transaction. Public eGovPay examples use the
`test_` prefix and describe it as test mode, but they do not establish the shape of the opaque
gateway credential issued for the supplied base URL. The adapter accepts a nonempty issued key without
whitespace or control characters and sends its test-decorated header. The explicit
demo configuration and provider verification select the sandbox. Status responses must contain
`environment_type: "TEST"`. Creation responses do not document an environment field; if one is
present it must also be `TEST`.

## Application workflow

`workflow_options_v2(p_quote_id,p_request_id)` preserves the original projection and adds a
top-level `egovpay` boolean to quote options. The original `workflow_options` is unchanged.
Availability and submission require the current actor/profile/Terms checks, an owned current
quote, demo profile and service, required PHP fee, and enabled demo configuration with a present
server credential. Cash and free submission keep their existing rules.

| Endpoint | Exact request | Successful response |
| --- | --- | --- |
| `start-egovpay` | `{payment_id,idempotency_key}` | `{checkout_url}` |
| `refresh-egovpay` | `{payment_id}` | `{status}` |

Both endpoints validate the signed-in session and an owner-only RPC before the service-only
server operation. Status is `pending`, `paid`, `unknown`, `failed`, or `cancelled`. Unknown lookup
outcomes remain unknown. The browser never supplies amount, currency, merchant reference, URLs,
or payment status. Credentials never reach the client. Checkout links are limited to the observed
test host and matching provider UUID. Callback and return URLs come from the trusted server URL.

One database claim owns each payment's creation key, txnid, callback capability, exact amount,
currency, owner, and request. Same-key retries reuse a saved checkout. A fresh screen key may
resume the same ready checkout; its owner/key alias stays bound to that payment. Cross-payment
key reuse conflicts, and a different key cannot retry a creating or unresolved checkout.
The first claim alone may POST. Any ambiguous POST or lost receipt remains blocked from creating
another transaction, even when a subsequent retry uses the same key. A receipt persisted before
a lost database response can be recovered by the same key. If the provider accepted a POST but
its UUID was never saved, manual provider reconciliation is required; the verified API does not
provide lookup by txnid or documented POST idempotency.
`CHECKOUT_UNCONFIRMED` (HTTP 409) distinguishes these uncertain outcomes from a disabled or
invalid configuration (`PROVIDER_UNAVAILABLE`). It never includes provider diagnostics.

The public callback accepts only a server-issued capability and a bounded body. The body is ignored;
it only triggers authenticated provider GET. Its uniform response contains no record information.
The return page is static and cannot change financial state. Callback/refresh verify TEST, UUID,
txnid, positive decimal amount without floating-point conversion, PHP currency, and stored binding.
SQL repeats those checks before mutation. Unsupported statuses cannot mark paid.

Reconciliation locks request, payment, then checkout. Verified PAID inserts a deterministic event
into the existing append-only provider financial ledger and increments the payment's
version once in the same transaction. Replays preserve the version; a conflicting ledger event
fails closed. `confirmed_at` records server verification time and `confirmed_by` remains null.
The request's status/version is unchanged. Bound transactions still receive an authenticated
provider lookup after request cancellation/rejection. A verified late PAID is recorded financially
and returned as paid while the request remains cancelled/rejected. Reconciliation never reopens
or advances the request. New submissions and checkout claims require acceptance of the current
active Terms; reconciliation can still record financial truth for an existing transaction.

The authenticated `start-egovpay` and `refresh-egovpay` handlers validate the current user and owner-only RPC. Gateway JWT verification is disabled consistently with the project's other explicitly authenticated handlers. Callback and static return routes need public gateway access; the callback still
requires its opaque per-payment capability and authoritative server verification. Apply only the
new additive migration to an existing installation; never replay the foundation or replace Vault keys.

## Remaining provider confirmation

The supplied base URL, test merchant token, settlement template, creation, and authoritative
lookup have been exercised. The supplied client secret is not substituted for the merchant key.
Monetary precision beyond the exercised decimal fixtures remains unspecified by public docs.

Obtain the callback body, authentication/signature rules, retries, acknowledgement requirements,
event identifiers, and the complete status enum. The user's previous sandbox exposed payment
confirmed/failed buttons on hosted checkout; these controls and their callback payloads are not
described by the public developer page. No separate demonstration endpoint is implemented.

`INITIAL` is `pending`; verified `PAID` plus nonempty `paid_at` is `paid`. The complete failure,
cancellation and expiration enums, callback signatures/retry/acknowledgement protocol, and provider
creation deduplication are still unverified. Callback authentication is not treated as proof of payment.

## Offline verification

Run with Node.js 24 or later:

```text
node --disable-warning=ExperimentalWarning --test backend/tests/egovpay.test.mjs
node --disable-warning=ExperimentalWarning --test backend/tests/egovpay-workflow.test.mjs
node --wasm-num-compilation-tasks=1 --max-old-space-size=384 --test backend/tests/egovpay-checkout.test.mjs
```

The checks strip types from the actual adapter and replace only fetch with local responses.
Fake prefixed and opaque hexadecimal tokens are used for HMAC fixtures, request construction, disabled/demo gates,
URL validation, response validation, unknown statuses, test environment enforcement, controlled
errors, and timeouts covering fetch and JSON decoding. Workflow and SQL checks cover ownership,
account readiness, exact inputs, configuration gates, retry identity, ambiguous outcomes, callback
forgeries, identity/amount/currency mismatches, cancellation, and atomic PAID replay. The opt-in
`egovpay-races.test.mjs` uses an isolated loopback PostgreSQL database and observes real row-lock
waits for concurrent claims, paid replays and cancellation. These checks do not verify provider
credentials or financial settlement.

Provider calls disable redirects, cookies and caching. Errors expose only controlled codes and
HTTP status; provider bodies and thrown diagnostics are not logged or returned. Merchant tokens
are rejected in configured, callback, redirect, or checkout URLs. No automatic retries are made.
