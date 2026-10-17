# eGovPay adapter contract

The adapter is source only and disabled unless a trusted server caller explicitly sets
`integrationEnabled: true` and `demoOnly: true`. This module does not expose a deployed endpoint,
create payment records, handle callbacks, or have an Android caller. It does not store credentials;
its tests use fake tokens and local responses. Provider confirmation and server authorization must
be completed before enabling it.

## Verified provider contract

The [current official catalog](https://platforms.e.gov.ph/api-catalogs/egovpay) documents:

| Operation | Route | Successful response |
| --- | --- | --- |
| Create payment | `POST {base_url}/api/v1/transaction` | HTTP 201, `data.uuid` and `data.url`; current example also includes `data.channel.refno` |
| Check transaction | `GET {base_url}/api/v1/transaction/{uuid}` | HTTP 200, transaction details including `environment_type` and `payment_status` |

Both routes use `X-eGovPay-Token`. Requests use `application/json; charset=utf-8`.
Required creation fields are `amount`, `items` with `name` and `amount`, `settlement_template_uuid`,
`redirect_url`, `callback_url`, `txnid`, and `digest`. Currency is optional; this adapter sends `PHP`.
The digest is HMAC-SHA256 over the exact `amount + "|" + txnid`, keyed by the merchant API token.

The [supplied developer page](https://egovpay.gov.ph/developers) is a public JavaScript application.
Its linked developer definitions use JSON strings for amounts and show `TEST` with `INITIAL` in
the status example. Its checkout response omits the channel reference. The current catalog labels
amounts as doubles and mixes numeric request examples with string amounts in saved requests.
The adapter preserves positive decimal strings exactly, emits lowercase hexadecimal HMAC, and
does not convert units, round amounts, or calculate fees.

The [portal overview](https://platforms.e.gov.ph/) identifies its services as staging.
The catalog deliberately omits the base URL because it is issued with credentials. The exact
credential-issued URL still needs its environment confirmed. Public eGovPay examples use the
`test_` prefix and describe it as test mode, but they do not establish the shape of the opaque
gateway credential issued for the supplied base URL. The adapter accepts a nonempty token without
whitespace or control characters and does not infer its environment from its format. The explicit
demo configuration and provider verification select the sandbox. Status responses must contain
`environment_type: "TEST"`. Creation responses do not document an environment field; if one is
present it must also be `TEST`.

## Pending provider confirmation

Before enabling network calls, confirm the issued base URL, test merchant token, settlement
template, and any client ID/secret authentication required by that gateway. The supplied client
secret must not be substituted for the documented merchant token without that confirmation.
Confirm monetary units and permitted decimal spelling/precision, including the spelling used
when checking the digest. Public documentation does not specify those rules.

Obtain the callback body, authentication/signature rules, retries, acknowledgement requirements,
event identifiers, and the complete status enum. The user's previous sandbox exposed payment
confirmed/failed buttons on hosted checkout; these controls and their callback payloads are not
described by the public developer page. No separate demonstration endpoint is implemented.

`INITIAL` is returned as `state: "pending"`. Every other raw `payment_status`, including `SUCCESS`
or `PAID`, is returned as `state: "unknown"`. No status or browser return marks an application
request paid. A future integration must bind provider UUID, biller reference, owner, request,
amount and currency to server records, authorize creation, and apply verified events idempotently.
Creation retry/deduplication behavior also needs a provider contract before retries can be added.

## Offline verification

Run with Node.js 24 or later:

```text
node --disable-warning=ExperimentalWarning --test backend/tests/egovpay.test.mjs
```

The checks strip types from the actual adapter and replace only fetch with local responses.
Fake prefixed and opaque hexadecimal tokens are used for HMAC fixtures, request construction, disabled/demo gates,
URL validation, response validation, unknown statuses, test environment enforcement, controlled
errors, and timeouts covering fetch and JSON decoding. These checks do not verify provider
credentials or financial settlement.

Provider calls disable redirects, cookies and caching. Errors expose only controlled codes and
HTTP status; provider bodies and thrown diagnostics are not logged or returned. Merchant tokens
are rejected in configured, callback, redirect, or checkout URLs. No automatic retries are made.
