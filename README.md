# Animecomic release artifacts

This branch contains public, locally signed APK deliverables and their release publisher.
All future stable and testing releases publish only release APKs. Debug APKs and their
checksums are prohibited, including renamed debuggable packages. Debug builds are local only.
The `main` branch contains application source code. Version tags point to the exact source
commit recorded in `release.json`, not to this artifact branch.

Update the signed APKs, hashes, metadata and notes together, validate with
`python publish.py --validate-only` (Android build-tools required), then push this branch.
GitHub Actions verifies APK versions and certificates, uploads a draft, downloads and
hash-checks every asset, and publishes the release. Existing public release bytes are immutable.

Signing keystores, passwords, personal tokens, build logs and device screenshots do not belong here.

Testing versions use `0.x.x-beta.N` with `prerelease: true` in release.json.
They are published with `latest=false`; stable versions keep the Latest designation.
