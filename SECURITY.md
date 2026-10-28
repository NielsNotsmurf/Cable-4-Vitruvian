# Security

## Reporting a problem

Please report security problems privately through GitHub: the repository's **Security** tab,
**Report a vulnerability**. Don't open a public issue for them.

## What the app and web app do with data

- Everything stays on the device: the Android app in its own storage, the web app in the
  browser's storage. There are no accounts, no servers and nothing to sign in to.
- The web app sends only anonymous usage counts (GoatCounter: no cookies, no IDs, no workout
  data), only from the hosted beta, and they can be switched off in its settings.
- The web app runs under a strict Content Security Policy: its own files only, plus the
  exercise demo media host and the counts endpoint.

## Checks on every change

- Dependency review on pull requests blocks new dependencies with known high-severity issues.
- CodeQL scans the Kotlin code and the workflows weekly and on every push to master.
- Dependabot proposes dependency and action updates weekly.
