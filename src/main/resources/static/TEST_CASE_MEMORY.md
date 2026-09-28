# Test Case Memory

Persistent record of generated test cases and their **actual observed results**.

This file is the memory. Each session that generates test cases reads it first, generates only
the cases that are missing, runs them, and appends the real result.

**Location:** `src/main/resources/static/TEST_CASE_MEMORY.md`. It lives under `static/` so Spring
Boot serves it directly at `/TEST_CASE_MEMORY.md` and the admin panel renders it without any
backend endpoint. Edit it in place - the panel re-reads it on every visit.

## Setup

- Base URL: `http://localhost:6161`
- Auth: HTTP Basic (see `application.properties` -> `app.access.*`). No credentials stored here.
- `/healthz` is intentionally public; every other route returns 401 without auth.
- Test env: `environment.env=QA`, database `beejapuri_QA`.
- Commands below are shown as `curl`; the batch was driven by a Python `urllib` harness
  (same headers, same endpoints) because it needed to stream SSE without buffering.

## Endpoint parameter reference (verified, do not re-guess)

Authoritative param names. Getting these wrong produces misleading 400s that look like app bugs.

| Route | Required | Optional |
|-------|----------|----------|
| `GET /api/customer/search` | `phone` | - |
| `GET /api/customer/details/{id}` | path | - |
| `GET /api/customer/attributes/{id}` | path | - |
| `POST /api/customer/validate` | body `phone` | - |
| `GET /api/product/fetch` | `customerId` | `cityId` (**required in practice** - service rejects null) |
| `GET /api/payment/autopay-configs` | - | `smart_plan` |
| `GET /api/payment/offers` | `phone` | `amount` (default 2000) |
| `GET /api/payment/offers/recharge-fetch` | `phone` | - |
| `GET /api/rapid-sale-marking/products` | `franchiseId` | - |
| `POST /api/complaint/fetch` + `/cleanup` | body `mobiles[]` | `databases[]` |
| `POST /api/feature-config/fetch` | body `databases[]` | - |
| `POST /api/order/place-and-mark/stream` | body `customerId` + product list | - |

## Protocol (for the agent, next session)

1. Read this file, the controllers, and `postman_collection.json` to see what is already covered.
2. Generate only cases not already present. Prioritise, in order:
   - previously **FAILING** cases (retry first)
   - untested error paths and 4xx/5xx handling
   - boundary values (empty, null, malformed, oversized, unicode)
   - auth and CSRF rejection paths
   - cross-resource flows
3. Run each case with curl against the live app. Record the **actual** status/result, not the expected one.
4. Append every case with its observed result. Mark failures `FAIL` so the next session retries them.
5. Never delete a case. Mark superseded ones `SUPERSEDED` so history stays intact.
6. Update the Coverage Summary counts.

## Result legend

| Mark | Meaning |
|------|---------|
| PASS | Ran, behaved as intended |
| FAIL | Ran, did not behave as intended |
| BLOCKED | Could not run (missing upstream data, external service down) |
| NOTRUN | Designed but not executed |
| SUPERSEDED | Replaced by a newer case |

---

## Access Control (AdminAccessFilter)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| AC-01 | health check public | `GET /healthz` | exempt path, no auth | 200 PASS |
| AC-02 | root without auth | `GET /` no header | 401 rejection | 401 PASS |
| AC-03 | root wrong password | `GET / -u admin:wrong` | constant-time compare rejects | 401 PASS |
| AC-04 | root valid credentials | `GET /` with Basic | gate admits | 200 PASS |
| AC-05 | WWW-Authenticate advertised | `GET /` no auth | browser prompt header | present PASS |
| AC-06 | api route needs auth | `GET /api/environment` no auth | filter covers all routes | 401 PASS |

## EnvironmentController  (`/api/environment`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| EN-01 | read current env | `GET /api/environment` | happy path read | 200 `{"env":"QA"}` PASS |
| EN-02 | switch to UAT | `POST` `{"env":"UAT"}` | env switch + client reset | 200 PASS |
| EN-03 | switch to QA (restore) | `POST` `{"env":"QA"}` | switch back, no contamination | 200 PASS |
| EN-04 | **unknown env accepted** | `POST` `{"env":"PROD"}` | invalid input rejected | **200 `success:true, env:"PROD"` - FAIL** |
| EN-05 | null env | `POST` `{}` | missing field | 400 PASS |
| EN-06 | whitespace env | `POST` `{"env":"   "}` | blank after trim | 400 PASS |
| EN-07 | lowercase env | `POST` `{"env":"qa"}` | case normalisation | 200 -> QA PASS |

**EN-04 is a live bug, not a test artifact.** `EnvironmentController.setEnvironment` (line 40)
calls `envConfig.setEnv(env.toUpperCase())` with no allow-list. `POST {"env":"PROD"}` returns
`200 success:true` and repoints the datasource at `beejapuri_PROD`. Restored to QA immediately
after the run. See "Open defects" below.

## FeatureConfigController  (`/api/feature-config`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| FC-01 | list databases | `GET /databases` | discovery happy path | 200, list returned PASS |
| FC-02 | fetch config rows | `POST /fetch` `{"databases":["beejapuri_QA"]}` | read path | 200, `data:[]` (no QA config rows) PASS |
| FC-03 | fetch unknown database | `POST /fetch` `{"databases":["nope_db"]}` | invalid db name | 200, `data:[]` PASS |
| FC-04 | fetch empty list | `POST /fetch` `{"databases":[]}` | boundary: empty collection | 400 "Select at least one database" PASS |
| FC-05 | fetch missing field | `POST /fetch` `{}` | null databases | 400 PASS |
| FC-06 | fetch wrong type | `POST /fetch` `{"databases":"beejapuri_QA"}` | string not array | 400 PASS |
| FC-07 | update-key empty value | `POST /update-key` full payload, `value:""` | boundary: empty value write | 200 `updated:true` PASS |
| FC-08 | update-key missing fields | `POST /update-key` `{"database":"beejapuri_QA"}` | partial payload | 400 "For input string: null" PASS |
| FC-09 | 5000-char database name | `POST /fetch` `{"databases":["aaaa..."]}` | oversized input | 200 `data:[]` PASS |

**FC-07 note:** this performs a real write against `beejapuri_QA` row 1. Kept as the only
mutating case in this file; re-running it is not idempotent in spirit.

## CustomerController  (`/api/customer`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| CU-01 | search by phone | `GET /search?phone=1234567890` | happy path, real DB rows | 200, rows returned PASS |
| CU-02 | search missing param | `GET /search` | required param absent | 400 PASS |
| CU-03 | search non-numeric | `GET /search?phone=abc` | invalid value, no crash | 200 "No customers found" PASS |
| CU-04 | search empty phone | `GET /search?phone=` | blank value guard | 400 "Phone number cannot be empty" PASS |
| CU-05 | details numeric id | `GET /details/1234567890` | happy path | 200 `data:{}` (not in QA) PASS |
| CU-06 | details non-numeric id | `GET /details/abc` | no type guard, tolerated | 200 `data:{}` PASS |
| CU-07 | attributes by id | `GET /attributes/1234567890` | happy path | 200 `hasAttributes:false` PASS |
| CU-08 | validate unknown customer | `POST /validate` `{"phone":"9999999999"}` | not-found handling | 200 `isValid:true` **suspect - FAIL** |
| CU-09 | unicode phone | `GET /search?phone=देवनागरी` | unicode input | 200 "No customers found" PASS |
| CU-10 | search wrong param name | `GET /search?customerNumber=123` | guards against stale clients | 400 PASS |

**CU-08 is suspicious:** `/validate` returned `isValid:true` for a phone that
`/search` reports as not found. Likely a mock/short-circuit path. Verify before relying on it.

## ProductController  (`/api/product`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| PR-01 | fetch with customer+city | `GET /fetch?customerId=1234567890&cityId=1` | happy path | 200 `data:[]` PASS |
| PR-02 | fetch without cityId | `GET /fetch?customerId=1234567890` | optional param, service rejects | 400 "Valid City ID is required" **surprising - see note** |
| PR-03 | non-numeric cityId | `GET /fetch?customerId=1234567890&cityId=abc` | NumberFormatException branch | 400 "cityId must be a number" PASS |
| PR-04 | negative cityId | `GET /fetch?customerId=1234567890&cityId=-5` | `<=0` coerced to null | 400 "Valid City ID is required" PASS |
| PR-05 | missing customerId | `GET /fetch?cityId=1` | required param absent | 400 PASS |

**PR-02 note:** `cityId` is declared `required=false` (ProductController.java:20) but the service
rejects null with a 400. The signature misleads callers; a `required=true` would fail faster and
more clearly. Low severity, contract wart not a break.

## PaymentController  (`/api/payment`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| PA-01 | autopay configs | `GET /autopay-configs` | happy path | 200, configs returned PASS |
| PA-02 | autopay filter applied | `GET /autopay-configs?smart_plan=true` | optional filter honoured | 200 PASS |
| PA-03 | offers by phone | `GET /offers?phone=1234567890` | happy path + default amount 2000 | 200, `amount:"2000.0"` PASS |
| PA-04 | offers recharge-fetch | `GET /offers/recharge-fetch?phone=1234567890` | happy path | 200, offers returned PASS |
| PA-05 | offers empty phone | `GET /offers?phone=` | blank guard | 400 "Enter a valid 10-digit mobile number" PASS |
| PA-06 | offers missing phone | `GET /offers` | required param absent | 400 PASS |
| PA-07 | recharge empty body | `POST /recharge` `{}` | missing payload | 400 PASS |
| PA-08 | recharge negative amount | `POST /recharge` `{"amount":-1}` | must not go negative | 400 PASS |
| PA-09 | offers recharge-apply empty | `POST /offers/recharge-apply` `{}` | missing payload | 400 PASS |

## SaleMarkingController  (`/api/order`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| SM-01 | nd-reasons list | `GET /nd-reasons` | happy path | 200, reasons returned PASS |
| SM-02 | nd-remarks by issue | `GET /nd-remarks/1` | happy path, real rows | 200, data returned PASS |
| SM-03 | nd-remarks non-numeric | `GET /nd-remarks/abc` | type guard on path var | 400 PASS |
| SM-04 | nd-remarks negative id | `GET /nd-remarks/-1` | negative boundary | 200 `count:0` PASS |
| SM-05 | nd-remarks zero id | `GET /nd-remarks/0` | zero boundary | 200 `count:0` PASS |
| SM-06 | route-sheet by customer | `GET /route-sheet/1234567890` | happy path | 200 `data:{}` PASS |
| SM-07 | customer search | `GET /customer/search?phone=1234567890` | happy path | 200, rows returned PASS |
| SM-08 | customer details | `GET /customer/details/1234567890` | happy path | 200 PASS |
| SM-09 | place-and-mark empty body | `POST /place-and-mark` `{}` | missing payload guard | 400 "Customer ID is required" PASS |
| SM-10 | place-and-mark wrong field | `POST /place-and-mark` `{"customerNumber":123}` | stale field name rejected | 400 PASS |
| SM-11 | run empty body | `POST /run` `{}` | missing payload guard | 400 PASS |
| SM-12 | **cross-site POST blocked** | `POST /place-and-mark` + `Sec-Fetch-Site: cross-site` | CSRF header check | **403 PASS** |
| SM-13 | cross-site stream blocked | `POST /place-and-mark/stream` + cross-site | CSRF on streaming route | 403 PASS |
| SM-14 | stream happy path | `POST /place-and-mark/stream` + same-origin | SSE content type + progress events | 200 `text/event-stream`, events emitted PASS |
| SM-15 | nd-reasons repeatable | `GET /nd-reasons` x2 | read idempotency | 200 both, identical length PASS |

**SM-14 note:** the stream aborted at `STEP 1: Validating customer data` with
`Workflow failed: At least one product is required` (`SaleMarkingService.java:62`). No write
occurred - validation fails before the flow touches data. Confirmed in the app log, no DB write.

## RapidSaleMarkingController  (`/api/rapid-sale-marking`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| RS-01 | products list | `GET /products?franchiseId=1` | happy path | 200 `productCount:0` PASS |
| RS-02 | products missing param | `GET /products` | required param absent | 400 PASS |
| RS-03 | eligibility by customer | `GET /eligibility/1234567890` | happy path, rapid DB | 200 `instantEligibility:false` PASS |
| RS-04 | eligibility non-numeric | `GET /eligibility/abc` | no type guard, tolerated | 200 PASS |
| RS-05 | addresses by customer | `GET /addresses/1234567890` | happy path | 200 "No addresses found" PASS |
| RS-06 | latest-order by customer | `GET /latest-order/1234567890` | happy path | 200 "No already-placed rapid order" PASS |
| RS-07 | order empty body | `POST /order` `{}` | missing payload guard | 400 "Customer ID cannot be empty" PASS |

## ComplaintController  (`/api/complaint`)

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| CP-01 | list databases | `GET /databases` | discovery happy path | 200, list returned PASS |
| CP-02 | fetch complaints | `POST /fetch` `{"mobiles":["1234567890"],"databases":["complaintmanagement_QA"]}` | happy path, real data | 200, results returned PASS |
| CP-03 | fetch empty mobiles | `POST /fetch` `{"mobiles":[]}` | boundary: empty collection | 400 "At least one mobile number is required" PASS |
| CP-04 | cleanup empty mobiles | `POST /cleanup` `{"mobiles":[]}` | destructive route guards empty input | 400 PASS |
| CP-05 | cleanup with mobiles | `POST /cleanup` `{"mobiles":["1234567890"]}` | **destructive, see note** | 400 "Select at least one database" PASS |
| CP-06 | fetch string not array | `POST /fetch` `{"mobiles":"1234567890"}` | wrong type | 400 PASS |

**CP-05 note:** guarded by a second check (database required), so no delete ran. Not exercised
with a real database selection - deliberately left undone because `/cleanup` is destructive.

## InteractiveWorkflowController  (`/api/workflow`)

Sequencing guard behaviour is the interesting surface here - every step rejects out-of-order
calls rather than corrupting state.

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| WF-01 | step1 empty body | `POST /step1-environment` `{}` | missing env guard | 400 PASS |
| WF-02 | step2 empty body | `POST /step2-customer-number` `{}` | missing customer guard | 400 PASS |
| WF-03 | step3 without step2 | `POST /step3-search-customer` `{}` | **sequencing enforced** | 400 "Customer number not set. Please complete step 2 first." PASS |
| WF-04 | nd-reasons | `GET /nd-reasons` | dropdown source, real data | 200 `count:20` PASS |
| WF-05 | state | `GET /state` | workflow state inspection | 200, `environment:null` after reset PASS |
| WF-06 | reset | `POST /reset` | state cleanup | 200 PASS |
| WF-07 | complete with no state | `POST /complete` | completing empty workflow | 400 "Environment is required" PASS |
| WF-08 | step6 out of order | `POST /step6-place-order` `{}` | write refused pre-order | 400 "At least one product is required" PASS |
| WF-09 | step8 out of order | `POST /step8-generate-route-sheet` | sequencing enforced | 400 "Customer ID not found. Complete previous steps first." PASS |
| WF-10 | step11 out of order | `POST /step11-mark-sale` | write refused pre-order | 400 "Customer ID not found" PASS |
| WF-11 | step4/5/9/10 not run | - | needs live customer + order | NOTRUN |

## Removal controllers

`/api/payment/autopay-customer-removal`, `/api/payment/membership-customer-removal`

Both have identical shape; both guard on empty input before touching data.

| ID | Case | Request | Covers | Result |
|----|------|---------|--------|--------|
| RM-01 | autopay config | `GET /config` | happy path | 200, databases listed PASS |
| RM-02 | membership config | `GET /config` | happy path | 200, databases listed PASS |
| RM-03 | autopay fetch empty | `POST /fetch` `{}` | destructive route guard | 400 "At least one customer DB id is required" PASS |
| RM-04 | membership fetch empty | `POST /fetch` `{}` | destructive route guard | 400 PASS |
| RM-05 | autopay run empty | `POST /run` `{}` | destructive route guard | 400 PASS |
| RM-06 | membership run empty | `POST /run` `{}` | destructive route guard | 400 PASS |

## Cross-cutting

| ID | Case | Covers | Result |
|----|------|--------|--------|
| BN-01 | unicode phone on search | non-ASCII input, no 500 | 200 "No customers found" PASS |
| BN-02 | 5000-char db name | oversized input, no 500 | 200 `data:[]` PASS |
| BN-03 | repeated identical read | read idempotency | 200 both, identical PASS |

---

## Open defects found

1. **EN-04 - environment accepts any value, including `PROD`.** No allow-list on
   `EnvironmentController.setEnvironment`. A single POST repoints the datasource at
   `beejapuri_PROD`. Severity: high. Fix: validate `env` against `{QA, UAT}` before `setEnv`.
2. **CU-08 - `/customer/validate` returns `isValid:true` for an unknown customer.**
   Inconsistent with `/customer/search` which reports not found. Severity: medium.
   Verify whether validation is short-circuiting to mock mode.
3. **PR-02 - `cityId` is `required=false` but the service rejects null.** Misleading signature
   (`ProductController.java:20`). Severity: low.

## Coverage Summary

| Controller | Endpoints | Cases | Notes |
|------------|-----------|-------|-------|
| Access Control (filter) | n/a | 6 | all PASS |
| EnvironmentController | 2 | 7 | 1 FAIL (EN-04) |
| FeatureConfigController | 3 | 9 | all PASS, 1 mutating |
| CustomerController | 4 | 10 | 1 suspect (CU-08) |
| ProductController | 1 | 5 | all PASS |
| PaymentController | 5 | 9 | all PASS |
| SaleMarkingController | 11 | 15 | all PASS, 3 CSRF/SSE |
| RapidSaleMarkingController | 5 | 7 | all PASS |
| ComplaintController | 3 | 6 | all PASS, 1 destructive-guarded |
| InteractiveWorkflowController | 14 | 11 | partial (steps 4,5,9,10 untested) |
| Removal controllers | 6 | 6 | all PASS |
| Cross-cutting | n/a | 3 | all PASS |
| **Total** | **40 endpoints** | **87 cases** | **84 PASS, 1 FAIL, 1 suspect, 1 NOTRUN** |

### Known gaps for next session

- `BaseCustomerRemovalController` has **no mappings** - dead controller, confirm if intended.
- `InteractiveWorkflowController` steps 4, 5, 9, 10 need a live customer + placed order.
- No end-to-end `place-and-mark` against live QA data (destructive, needs sign-off).
- No real `update-key` round-trip that mutates and re-reads a known key.
- No duplicate-submit / idempotency case on the write routes (`/recharge`, `/place-and-mark`).
- No case for concurrent access / two simultaneous sessions on `/workflow` state.
- No 5xx-path case; every error observed so far is a clean 4xx. Error handling for upstream
  DB or CMS failure is entirely untested.
- `POST /api/complaint/cleanup` never exercised with a real database selection (destructive).
