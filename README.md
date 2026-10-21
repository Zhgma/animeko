# Animecomic release artifacts

This branch contains public release packages and their release publisher.
Android stable and testing releases publish only signed release APKs. Debug APKs and their
checksums are prohibited, including renamed debuggable packages. Debug builds are local only.
The `main` branch contains application source code. Version tags point to the exact source
commit recorded in `release.json`, not to this artifact branch.

Update the signed APKs, hashes, metadata and notes together, validate with
`python publish.py --validate-only` (Android build-tools required), then push this branch.
GitHub Actions verifies APK versions and certificates, uploads a draft, downloads and
hash-checks every asset, and publishes the release. Existing public release bytes are immutable.

Windows x64 packages use `ani-<version>-windows-x86_64.zip`, built with
`:app:desktop:createReleaseDistributable` from the exact tagged source commit. The archive
contains `Ani/Ani.exe`, the bundled Java/JCEF/native runtime and `Ani/release-manifest.json`.
Distribute the complete extracted folder. Windows executables are not Authenticode signed.
Attach matching `.sha1` and `.sha256` files for OTA integrity and manual verification.
Large archives are stored as ordered parts below GitHub's per-file limit. Each `parts`
entry in release.json records its name, size and SHA-256; the asset also records the
complete archive's size and SHA-256. The publisher reassembles into ignored `assembled/`
and verifies all bytes and the archive layout before uploading the complete ZIP.
An existing public release may receive missing platform packages. Its existing assets
are downloaded and verified before any upload, and are never overwritten. Channel,
release notes and Latest designation remain unchanged when adding a platform.

After building and running the packaged runtime checks, prepare the Windows archive with
`python prepare_windows.py <clean-source-checkout> <local-output-directory>`.
This verifies the compiled version/commit, creates the ZIP and checksum files, splits
the ZIP into 48 MiB Git objects, and adds the asset metadata to `release.json`.
Run `python -m unittest -v test_publish.py` and `python publish.py --validate-only`
before committing the prepared files and pushing `release-artifacts`.
`verify_windows.ps1 -ImageDirectory <path-to-Ani> -LogDirectory <local-logs>` runs the
packaged BT, FFmpeg, SQLite and JCEF smoke tests. Logs stay outside the publisher checkout.

Signing keystores, passwords, personal tokens, build logs and device screenshots do not belong here.

Testing versions use `0.x.x-beta.N` with `prerelease: true` in release.json.
They are published with `latest=false`; stable versions keep the Latest designation.
