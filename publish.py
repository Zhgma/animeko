"""Publish locally signed release artifacts; private signing keys stay on the build host."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(*args, check=True):
    return subprocess.run(args, check=check, capture_output=True, text=True, encoding="utf-8")


def validate():
    meta = json.loads((ROOT / "release.json").read_text(encoding="utf-8"))
    version = meta["version"]
    require(re.fullmatch(r"0\.\d+\.\d+", version), "Expected fork 0.x.x version")
    require(meta["tag"] == version, "Tag/version mismatch")
    require(re.fullmatch(r"[0-9a-f]{40}", meta["sourceCommit"]), "Invalid source commit")
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    suffix = ".bat" if os.name == "nt" else ""
    signers = sorted((sdk / "build-tools").glob("*/apksigner" + suffix))
    require(signers, "Android build-tools apksigner is required")
    signer = signers[-1]
    aapt = signer.parent / ("aapt.exe" if os.name == "nt" else "aapt")
    files = []
    for name, expected in meta["assets"].items():
        require(re.fullmatch(r"[A-Za-z0-9._-]+", name), "Unsafe asset name")
        p = ROOT / "assets" / name
        require(p.is_file() and not p.is_symlink(), "Missing asset: " + name)
        require(p.stat().st_size == expected["size"] and digest(p) == expected["sha256"], "Asset bytes differ: " + name)
        if name.endswith(".apk"):
            with zipfile.ZipFile(p) as z:
                require(z.testzip() is None, "Damaged APK")
            certificate = run(str(signer), "verify", "--print-certs", str(p)).stdout
            require("certificate SHA-256 digest: " + expected["certificateSha256"] in certificate, "APK signer differs")
            badging = run(str(aapt), "dump", "badging", str(p)).stdout
            require(f"name='{expected['package']}' versionCode='{meta['versionCode']}' versionName='{version}'" in badging, "APK package/version differs")
            require("sdkVersion:'27'" in badging and "native-code: 'arm64-v8a'" in badging, "APK platform differs")
        files.append(p)
    require(len([p for p in files if p.suffix == ".apk"]) == 2, "Expected release and debug APKs")
    return meta, files


def verify_uploaded(tag, files):
    with tempfile.TemporaryDirectory() as directory:
        for file in files:
            run("gh", "release", "download", tag, "--dir", directory, "--pattern", file.name)
            require(digest(Path(directory) / file.name) == digest(file), "Uploaded bytes differ: " + file.name)


def main():
    meta, files = validate()
    print("Validated signed APKs and checksums", flush=True)
    if "--validate-only" in os.sys.argv:
        return
    require(os.environ.get("GH_REPO") == "Zhgma/animeko", "Unexpected repository")
    tag = meta["tag"]
    target = run("gh", "api", f"repos/Zhgma/animeko/git/ref/tags/{tag}", "--jq", ".object.sha").stdout.strip()
    require(target == meta["sourceCommit"], "Release tag must point at the source commit")
    existing = run("gh", "release", "view", tag, "--json", "isDraft,isPrerelease", check=False)
    if existing.returncode == 0:
        state = json.loads(existing.stdout)
        if not state["isDraft"]:
            require(not state["isPrerelease"], "Unexpected prerelease")
            verify_uploaded(tag, files)
            print("Published release already matches; left unchanged")
            return
    else:
        run("gh", "release", "create", tag, "--verify-tag", "--draft", "--title", f"Animecomic {tag}", "--notes-file", str(ROOT / "release-notes.md"))
    run("gh", "release", "upload", tag, *(str(p) for p in files), "--clobber")
    verify_uploaded(tag, files)
    run("gh", "release", "edit", tag, "--draft=false", "--latest")
    print(f"Published https://github.com/Zhgma/animeko/releases/tag/{tag}")


if __name__ == "__main__":
    main()
