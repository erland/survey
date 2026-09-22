# Security baseline

The MVP security baseline is intentionally small and self-hosting friendly.

## Administrative session

- Admin sessions use an `HttpOnly` cookie.
- Production keeps the cookie `Secure`.
- `SameSite=Strict` is used to reduce cross-site request risk.
- Only a SHA-256 hash of the random session token is stored server-side.
- Logout revokes the server-side session.
- Unsafe admin/logout requests carrying an `Origin` header must have the same authority as the request `Host`.

## Participant and presentation tokens

- Participant and presentation tokens are random bearer credentials.
- Only hashes are persisted.
- Tokens and free-text response bodies must not be written to application logs.
- Presentation tokens are read-only, expiring and revocable.

## Request limits

- HTTP request bodies default to a maximum of `1M` (`HTTP_MAX_BODY_SIZE`).
- In-memory rate limiting is applied to login, public participant endpoints and presentation endpoints.
- Defaults are deliberately generous for workshop traffic and configurable through environment variables.
- The in-memory limiter is per backend instance. A multi-instance deployment should move rate limiting to a trusted reverse proxy/API gateway or shared store.

## Proxy requirement

The rate limiter uses `X-Forwarded-For`/`X-Real-IP` when there is no participant token. A production reverse proxy must **overwrite and sanitize** these headers rather than trusting values supplied by the internet client.

## Browser security

- API responses receive `nosniff`, `DENY` framing, `no-referrer`, a restrictive permissions policy and `no-store` caching.
- `frontend/index.html` includes a restrictive CSP suitable for the current frontend.
- The deployment/reverse proxy should also emit CSP and the other security headers on the static frontend response because HTTP response headers are stronger than a CSP meta element.

## Logging

A source review found no application logging of participant tokens, presentation tokens, passwords or free-text bodies. Future request logging must avoid headers such as `X-Participant-Token`, `Cookie`, `Authorization`, request bodies and exported response content.

## Out of scope for this step

- distributed/shared rate limiting
- WAF/bot challenge
- enterprise OIDC/SSO
- automated secret scanning and dependency vulnerability scanning (can be added in CI/deployment hardening)
