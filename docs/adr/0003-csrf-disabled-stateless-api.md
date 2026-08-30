# ADR 0003: CSRF protection disabled for the stateless API

**Status:** Accepted
**Date:** 2026-08-30
**Context:** GitHub CodeQL alert "Disabled Spring CSRF protection"
(`SecurityConfig.java`: `http.csrf(csrf -> csrf.disable())`)

## Context

`auth-api` exposes `POST /v1/authorizations` to merchant backends. It is a
machine-to-machine API: no browser, no login form, no cookies.

## Decision

CSRF protection is disabled application-wide, together with:

- `SessionCreationPolicy.STATELESS` — the server stores no session state
- Authentication via a custom `X-API-Key` request header (not ambient credentials)

## Rationale

CSRF exploits **ambient credentials**: the victim's browser automatically
attaches cookies to cross-site requests, so a malicious page can act as the
user. Custom request headers are *not* ambient — a forged form/image request
cannot set `X-API-Key`, and setting one requires a CORS preflight that this
API does not grant. With no cookies and no session, there is no ambient
credential for a CSRF attack to abuse; a forged cross-site request arrives
unauthenticated and is rejected with 401.

Requiring CSRF tokens would (a) secure nothing in this threat model and
(b) break every merchant integration that calls the API from a backend
(curl, Java HTTP clients — none can participate in a browser token dance).

This follows OWASP guidance: CSRF defenses are required where cookie/session
authentication exists. If this API ever serves browser clients with cookie
sessions, CSRF protection must be re-enabled for those routes (and this ADR
revisited — see Phase 4, where OAuth2/JWT between services stays stateless).

## Consequences

- CodeQL will keep flagging `csrf.disable()`; the alert is dismissed as a
  false positive with reference to this ADR. Revisit if cookie-based auth is
  ever introduced.
- Alternative considered: enable CSRF and require a token header on all
  mutations — rejected as above (friction without threat-model gain).
