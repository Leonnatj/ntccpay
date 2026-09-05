Feature: Authorization decision
  As the payment platform
  I decide each authorization request by applying business rules
  So that only legitimate payments within limits are approved

  Business rules (evaluated in order): the PAN must be valid (Luhn),
  the card must not be blocklisted, the currency must be supported,
  and the amount must not exceed the limit.
  The first failing rule determines the Reason Code.

  Background:
    Given the supported currencies are "USD", "EUR" and "GBP"
    And the per-transaction limit is 50000 minor units
    And the card "4000000000000002" is blocklisted
    And the BIN "400100" is blocklisted

  Scenario: A normal authorization is approved
    Given a valid card with PAN "4242424242424242"
    When an authorization is requested for 1000 minor units in "USD" by merchant "acme-corp"
    Then the decision is APPROVED

  Scenario: An amount above the limit is declined
    Given a valid card with PAN "4242424242424242"
    When an authorization is requested for 50001 minor units in "USD" by merchant "acme-corp"
    Then the decision is DECLINED with reason code "AMOUNT_EXCEEDS_LIMIT"

  Scenario: An unsupported currency is declined
    Given a valid card with PAN "4242424242424242"
    When an authorization is requested for 1000 minor units in "JPY" by merchant "acme-corp"
    Then the decision is DECLINED with reason code "CURRENCY_NOT_SUPPORTED"

  Scenario: A blocklisted card is declined
    Given a valid card with PAN "4000000000000002"
    When an authorization is requested for 1000 minor units in "USD" by merchant "acme-corp"
    Then the decision is DECLINED with reason code "CARD_BLOCKED"

  Scenario: A card from a blocked BIN is declined
    Given a valid card with PAN "4001001234567898"
    When an authorization is requested for 1000 minor units in "USD" by merchant "acme-corp"
    Then the decision is DECLINED with reason code "CARD_BLOCKED"

  Scenario: A PAN failing the Luhn check is declined
    Given an invalid card with PAN "4242424242424241"
    When an authorization is requested for 1000 minor units in "USD" by merchant "acme-corp"
    Then the decision is DECLINED with reason code "INVALID_PAN"
