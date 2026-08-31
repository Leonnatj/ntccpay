package com.ntccpay.auth.bdd;

import com.ntccpay.auth.application.port.in.AuthorizationCommand;
import com.ntccpay.auth.application.port.in.AuthorizationResult;
import com.ntccpay.auth.application.usecase.AuthorizationRequestService;
import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.domain.model.ReasonCode;
import com.ntccpay.auth.domain.service.AuthorizationRuleEngine;
import com.ntccpay.auth.testing.InMemoryAuthorizationRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the app through its ports: no HTTP, no Spring. Rules are proven, not plumbing. */
public class AuthorizationStepDefinitions {

    private static final Set<String> DEFAULT_CURRENCIES = Set.of("USD", "EUR", "GBP");
    private static final long DEFAULT_LIMIT = 50_000;

    private InMemoryAuthorizationRepository repository;
    private AuthorizationRequestService service;

    private Set<String> currencies = new HashSet<>(DEFAULT_CURRENCIES);
    private long limit = DEFAULT_LIMIT;
    private Set<String> blockedCards = new HashSet<>();
    private Set<String> blockedBins = new HashSet<>();

    private String pan;
    private String currency;
    private String merchant;
    private long lastAmount;

    private final List<AuthorizationResult> received = new ArrayList<>();
    private Exception caught;

    @Before
    public void resetScenarioState() {
        repository = new InMemoryAuthorizationRepository();
        currencies = new HashSet<>(DEFAULT_CURRENCIES);
        limit = DEFAULT_LIMIT;
        blockedCards = new HashSet<>();
        blockedBins = new HashSet<>();
        rebuild();
        pan = null;
        currency = null;
        merchant = null;
        received.clear();
        caught = null;
    }

    private void rebuild() {
        var engine = new AuthorizationRuleEngine(currencies, limit, blockedCards, blockedBins);
        service = new AuthorizationRequestService(repository, engine);
    }

    @Given("the supported currencies are {string}, {string} and {string}")
    public void theSupportedCurrenciesAre(String a, String b, String c) {
        currencies = new HashSet<>(List.of(a, b, c));
        rebuild();
    }

    @Given("the per-transaction limit is {long} minor units")
    public void thePerTransactionLimitIs(long limit) {
        this.limit = limit;
        rebuild();
    }

    @Given("the card {string} is blocklisted")
    public void theCardIsBlocklisted(String pan) {
        blockedCards.add(pan);
        rebuild();
    }

    @Given("the BIN {string} is blocklisted")
    public void theBinIsBlocklisted(String bin) {
        blockedBins.add(bin);
        rebuild();
    }

    @Given("a valid card with PAN {string}")
    public void aValidCardWithPan(String pan) {
        this.pan = pan;
    }

    @Given("an invalid card with PAN {string}")
    public void anInvalidCardWithPan(String pan) {
        this.pan = pan;
    }

    @When("an authorization is requested for {long} minor units in {string} by merchant {string}")
    public void anAuthorizationIsRequested(long amount, String currency, String merchant) {
        request(amount, currency, merchant, null);
    }

    @When("an authorization is requested for {long} minor units in {string} by merchant {string} with idempotency key {string}")
    public void anAuthorizationIsRequestedWithKey(long amount, String currency, String merchant, String key) {
        request(amount, currency, merchant, key);
    }

    @When("the same request is retried with idempotency key {string}")
    public void theSameRequestIsRetriedWithKey(String key) {
        request(lastAmount, currency, merchant, key);
    }

    @When("a different amount of {long} minor units is requested with idempotency key {string}")
    public void aDifferentAmountIsRequestedWithKey(long amount, String key) {
        request(amount, currency, merchant, key);
    }

    private void request(long amount, String currency, String merchant, String key) {
        this.currency = currency;
        this.merchant = merchant;
        this.lastAmount = amount;
        try {
            received.add(service.handle(new AuthorizationCommand(
                    pan, amount, currency, merchant,
                    key == null ? UUID.randomUUID().toString() : key)));
            caught = null;
        } catch (Exception e) {
            caught = e;
        }
    }

    private AuthorizationResult lastResult() {
        assertThat(received).isNotEmpty();
        return received.get(received.size() - 1);
    }

    @Then("the decision is APPROVED")
    public void theDecisionIsApproved() {
        assertThat(caught).isNull();
        assertThat(lastResult().decision()).isEqualTo(Decision.APPROVED);
    }

    @Then("the decision is DECLINED with reason code {string}")
    public void theDecisionIsDeclinedWithReasonCode(String reasonCode) {
        assertThat(caught).isNull();
        var result = lastResult();
        assertThat(result.decision()).isEqualTo(Decision.DECLINED);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.valueOf(reasonCode));
    }

    @Then("both requests receive the same decision")
    public void bothRequestsReceiveTheSameDecision() {
        assertThat(caught).isNull();
        assertThat(received).hasSize(2);
        var first = received.get(0);
        var second = received.get(1);
        assertThat(second.decision()).isEqualTo(first.decision());
        assertThat(second.reasonCode()).isEqualTo(first.reasonCode());
    }

    @Then("only one authorization was created")
    public void onlyOneAuthorizationWasCreated() {
        assertThat(repository.count()).isEqualTo(1);
    }

    @Then("two authorizations were created")
    public void twoAuthorizationsWereCreated() {
        assertThat(repository.count()).isEqualTo(2);
    }

    @Then("the platform reports an idempotency key conflict")
    public void thePlatformReportsAnIdempotencyKeyConflict() {
        assertThat(caught).isInstanceOf(com.ntccpay.auth.application.exception.IdempotencyConflictException.class);
    }
}
