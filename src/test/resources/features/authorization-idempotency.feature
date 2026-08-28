Feature: Idempotent authorization decisions
  A client retries a timed-out request. The platform must return the original
  decision instead of making a second one: one decision per idempotency key,
  and a decision, once made, is immutable.

  Background:
    Given the supported currencies are "USD", "EUR" and "GBP"
    And the per-transaction limit is 50000 minor units

  Scenario: A retried request with the same idempotency key returns the original decision
    Given a valid card with PAN "4242424242424242"
    When an authorization is requested for 1000 minor units in "USD" by merchant "acme-corp" with idempotency key "abc-123"
    And the same request is retried with idempotency key "abc-123"
    Then both requests receive the same decision
    And only one authorization was created

  Scenario: The same idempotency key with a different request is rejected
    Given a valid card with PAN "4242424242424242"
    When an authorization is requested for 1000 minor units in "USD" by merchant "acme-corp" with idempotency key "abc-123"
    And a different amount of 2000 minor units is requested with idempotency key "abc-123"
    Then the platform reports an idempotency key conflict

  Scenario: Two different idempotency keys create two independent decisions
    Given a valid card with PAN "4242424242424242"
    When an authorization is requested for 1000 minor units in "USD" by merchant "acme-corp" with idempotency key "abc-123"
    And the same request is retried with idempotency key "def-456"
    Then both requests receive the same decision
    And two authorizations were created
