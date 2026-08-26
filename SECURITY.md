# Security policy

Please do not disclose suspected vulnerabilities in a public issue. Use the
repository's **Security → Report a vulnerability** form so the report is sent
through GitHub private vulnerability reporting. Include a description, impact,
reproduction steps, and a proposed mitigation. We will acknowledge reports as
soon as practical and coordinate disclosure after a fix is available.

Supported releases are the current production release and the immediately
preceding release. Security fixes should be backported only when the affected
release is still supported.

## Release security checklist

- Set `SPRING_PROFILES_ACTIVE=prod`, `REMEMBER_ME_KEY`, `ORIGIN_SHARED_SECRET`,
  `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `GOOGLE_REDIRECT_URI` in the
  production environment.
- Rotate and invalidate the historical Tesco API key found in repository
  history (commit `8164cce`) before public release. The current source does
  not contain that key, but history exposure remains a release blocker.
- Keep the edge-injected `X-Origin-Shared-Secret` private; browsers must never
  receive it. Configure the edge to inject it and `X-Client-IP` only on
  requests forwarded to the application.
- Review dependency and secret-scanning workflow results before publishing a
  release.
