# Ubiquitous Language

The glossary of payment terms as the business uses them. Code, tests, Gherkin
features, and Kafka topics all use these exact words. If a test name can't be
read aloud to a payments person, rename it.

| Term | Meaning |
|---|---|
| **Authorization** | A request from a merchant to check whether a cardholder's account permits a payment, and the decision that results. The aggregate root of the Auth Context. |
| **AuthorizationRequest** | The inbound ask: PAN, amount, currency, merchant, idempotency key. |
| **Decision** | The outcome of an authorization: `APPROVED` or `DECLINED`. A decision, once made, is immutable. One decision per idempotency key. |
| **Decline** | A negative decision. Always carries a Reason Code. |
| **Reason Code** | The machine-readable why behind a decline: `AMOUNT_EXCEEDS_LIMIT`, `CURRENCY_NOT_SUPPORTED`, `CARD_BLOCKED`, `INVALID_PAN`. |
| **Capture** | Committing (all or part of) a previously approved authorization for settlement. |
| **Reversal** | Cancelling an authorization before capture; releases the hold. |
| **Cardholder** | The person or entity to whom the card was issued. |
| **PAN** | Primary Account Number — the card number. Toxic data: mask in logs (`****1111`), never store in full. |
| **BIN** | Bank Identification Number — the leading digits of the PAN identifying the issuing institution. |
| **Merchant** | The party requesting the payment. Identified by `MerchantId`. |
| **Idempotency Key** | Client-supplied key making a repeated request return the original decision instead of creating a second one. |
| **Money** | Amount in **minor units** (integer cents) plus ISO 4217 currency. Never floats. |
| **Limit** | The maximum amount permitted for a single authorization. Amounts above it are declined. |
| **Blocklist** | A set of cards or BINs that must always be declined with `CARD_BLOCKED`. |
| **Settlement** | End-of-day batching of captured authorizations and their reconciliation. |
| **Ledger** | The double-entry record: debits must equal credits. The ledger's "transaction" is not the auth's "authorization". |
