# Production deployment notes

The container defaults to the `prod` Spring profile. Treat the values below
as deployment inputs, not values to commit. Store secrets in the platform
secret manager and inject them at runtime.

## Required configuration

```text
SPRING_PROFILES_ACTIVE=prod
SQLITE_JDBC_URL=jdbc:sqlite:/var/lib/demo/app.db
REMEMBER_ME_KEY=<long random stable value>
ORIGIN_SHARED_SECRET=<long random edge-to-origin value>
GOOGLE_CLIENT_ID=<Google OAuth client id>
GOOGLE_CLIENT_SECRET=<Google OAuth client secret>
GOOGLE_REDIRECT_URI=https://proteinpereuro.ie/api/auth/google/callback
MISTRAL_API_KEY=<private OCR provider key>
```

`REMEMBER_ME_KEY` must remain stable across restarts. Production startup fails
when it is missing. `ORIGIN_SHARED_SECRET` is injected by the trusted edge as
`X-Origin-Shared-Secret`; it must not be exposed to Angular or another public
client. The edge may also inject the validated client address as
`X-Client-IP`. The application trusts that address only after the shared
secret has been verified.

## Browser and cookies

`/api/auth/csrf` is public and initializes the Angular-compatible
`XSRF-TOKEN` cookie. Unsafe requests must include `X-XSRF-TOKEN`. Session and
remember-me cookies are Secure, HttpOnly, and SameSite=Lax in production; the
CSRF cookie is intentionally readable by Angular and is Secure/SameSite=Lax.

## Google login

Sign-in is Google OAuth2 only. Users start at `GET /api/auth/google` and
return to `GET /api/auth/google/callback`. `GOOGLE_CLIENT_ID` and
`GOOGLE_CLIENT_SECRET` come from the Google Cloud OAuth client.
`GOOGLE_REDIRECT_URI` must match that client exactly.

## Proxy and resource boundaries

The origin filter exempts only `/api/health` and `/api/health/readiness`. Roll
it out in two deployments: first set `ORIGIN_VERIFY_ENABLED=true` and
`ORIGIN_VERIFY_ENFORCE=false`, confirm every edge request supplies the shared
secret, then set `ORIGIN_VERIFY_ENFORCE=true`. Production defaults both values
to true. Retailer lookups accept
only HTTPS URLs for exact allowlisted Irish retailer hostnames. Playwright
browser fallbacks allow only explicit retailer/API hosts and abort redirects or
subrequests to other hosts. OCR uploads are limited to three images and 10 MiB
per image by default; production also verifies image signatures.

The persistent remember-me table is created with `CREATE TABLE IF NOT EXISTS`
at startup. Review this against your database migration policy before changing
the schema.

The browser uses the same-origin `/api` proxy, so production CORS should remain
empty. Set `CORS_ALLOWED_ORIGINS` only for an intentional, separately reviewed
cross-origin client.
