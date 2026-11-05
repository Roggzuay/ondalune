# Security policy

## Reporting a vulnerability

Please **don't open a public issue** for security problems. Report them privately through
GitHub: go to the repository's **Security** tab and choose **Report a vulnerability**.

Include what you found, how to reproduce it, and which version of Ondalune you used (Settings →
About & licences). You'll get a reply within a week, and a fix as soon as it's practical. Credit
is given in the release notes unless you prefer otherwise.

## Supported versions

Only the latest release receives security fixes.

## Scope

In scope:
- The Android app in this repository.
- Its handling of server credentials and network traffic.

Out of scope:
- Vulnerabilities in your music server itself; report those to its project, such as Navidrome.
- Issues that need a rooted or already compromised phone.

## Secret scanning

Every push and pull request runs [Gitleaks](https://github.com/gitleaks/gitleaks) over the whole
git history (`.github/workflows/gitleaks.yml`, a pinned release checked against its SHA-256), with
the rules in `.gitleaks.toml`. Contributors can scan staged changes before each commit with
`git config core.hooksPath tools/git-hooks`.

The last full audit (5 November 2026, Gitleaks 8.30.1) covered every branch, tag, stash and
unreachable object and found no secrets in the repository's history. Release signing keys and
their passwords are kept outside the repository and are never committed.
