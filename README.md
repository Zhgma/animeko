# Animecomic release artifacts

This branch contains public, locally signed APK deliverables and their release publisher.
The `main` branch contains application source code. Version tags point to the exact source
commit recorded in `release.json`, not to this artifact branch.

Update the signed APKs, hashes, metadata and notes together, validate with
`python publish.py --validate-only` (Android build-tools required), then push this branch.
GitHub Actions verifies APK versions and certificates, uploads a draft, downloads and
hash-checks every asset, and publishes the release. Existing public release bytes are immutable.

Signing keystores, passwords, personal tokens, build logs and device screenshots do not belong here.
