"""Publish locally signed release artifacts; private signing keys stay on the build host."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def asset_path(name, expected):
    require(re.fullmatch(r"[A-Za-z0-9._-]+", name), "Unsafe asset name")
    if "parts" not in expected:
        return ROOT / "assets" / name
    parts = expected["parts"]
    require(isinstance(parts, list) and parts, "Empty asset parts")
    require(len({part["name"] for part in parts}) == len(parts), "Duplicate asset parts")
    output = ROOT / "assembled" / name
    output.parent.mkdir(exist_ok=True)
    with output.open("wb") as stream:
        for part in parts:
            require(re.fullmatch(re.escape(name) + r"\.part[0-9]{3}", part["name"]), "Unsafe part name")
            source = ROOT / "assets" / part["name"]
            require(source.is_file() and not source.is_symlink(), "Missing part: " + part["name"])
            require(source.stat().st_size == part["size"] and digest(source) == part["sha256"], "Part bytes differ: " + part["name"])
            with source.open("rb") as incoming:
                shutil.copyfileobj(incoming, stream)
    return output


def validate_windows_zip(path, meta):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate ZIP paths")
        require(all(not PurePosixPath(n).is_absolute() and ".." not in PurePosixPath(n).parts
                    and "\\" not in n and ":" not in n for n in names), "Unsafe ZIP paths")
        require(archive.testzip() is None, "Damaged Windows ZIP")
        required = {"Ani/Ani.exe", "Ani/app/Ani.cfg", "Ani/runtime/bin/java.dll",
                    "Ani/runtime/bin/jcef.dll", "Ani/runtime/bin/jcef_helper.exe", "Ani/release-manifest.json"}
        require(required.issubset(names), "Missing Windows runtime files")
        manifest = json.loads(archive.read("Ani/release-manifest.json"))
        require(manifest == dict(version=meta["version"], sourceCommit=meta["sourceCommit"],
                                 platform="windows", arch="x86_64", buildType="release"), "Windows manifest differs")
        config = archive.read("Ani/app/Ani.cfg").decode("utf-8")
        require("-Dani.debug=true" not in config and "-javaagent:" not in config, "Development Windows launcher")
        exe = archive.read("Ani/Ani.exe")
        require(exe[:2] == b"MZ" and len(exe) > 64, "Invalid Windows launcher")
        offset = struct.unpack_from("<I", exe, 60)[0]
        require(exe[offset:offset + 4] == b"PE\0\0" and
                struct.unpack_from("<H", exe, offset + 4)[0] == 0x8664, "Expected Windows x64 launcher")


def run(*args, check=True):
    return subprocess.run(args, check=check, capture_output=True, text=True, encoding="utf-8")


def validate():
    meta = json.loads((ROOT / "release.json").read_text(encoding="utf-8"))
    version = meta["version"]
    require(re.fullmatch(r"0\.\d+\.\d+(?:-(?:alpha|beta|rc)\.[1-9]\d*)?", version), "Expected fork 0.x.x version")
    require(meta["tag"] == version, "Tag/version mismatch")
    require(isinstance(meta.get("prerelease", False), bool), "Invalid prerelease flag")
    require(meta.get("prerelease", False) == ("-" in version), "Version/channel mismatch")
    require(re.fullmatch(r"[0-9a-f]{40}", meta["sourceCommit"]), "Invalid source commit")
    validate_public_assets(meta)
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    suffix = ".bat" if os.name == "nt" else ""
    signers = sorted((sdk / "build-tools").glob("*/apksigner" + suffix))
    require(signers, "Android build-tools apksigner is required")
    signer = signers[-1]
    aapt = signer.parent / ("aapt.exe" if os.name == "nt" else "aapt")
    files = []
    for name, expected in meta["assets"].items():
        p = asset_path(name, expected)
        require(p.is_file() and not p.is_symlink(), "Missing asset: " + name)
        require(p.stat().st_size == expected["size"] and digest(p) == expected["sha256"], "Asset bytes differ: " + name)
        if name.endswith(".apk"):
            with zipfile.ZipFile(p) as z:
                require(z.testzip() is None, "Damaged APK")
            certificate = run(str(signer), "verify", "--print-certs", str(p)).stdout
            require("certificate SHA-256 digest: " + expected["certificateSha256"] in certificate, "APK signer differs")
            badging = run(str(aapt), "dump", "badging", str(p)).stdout
            require("application-debuggable" not in badging, "Debuggable APKs must not be published")
            require(f"name='{expected['package']}' versionCode='{meta['versionCode']}' versionName='{version}'" in badging, "APK package/version differs")
            require("sdkVersion:'27'" in badging and "native-code: 'arm64-v8a'" in badging, "APK platform differs")
        elif name.endswith("-windows-x86_64.zip"):
            require(name == f"ani-{version}-windows-x86_64.zip", "Windows asset/version mismatch")
            validate_windows_zip(p, meta)
        files.append(p)
    return meta, files


def validate_public_assets(meta):
    assets = meta["assets"]
    require(not any("debug" in name.lower() for name in assets), "Debug artifacts must not be published")
    apks = [asset for name, asset in assets.items() if name.endswith(".apk")]
    require(apks, "Expected a signed release APK")
    require(all(asset.get("package") == "me.him188.ani" for asset in apks), "Expected release application ID")


def verify_uploaded(tag, files):
    with tempfile.TemporaryDirectory() as directory:
        for file in files:
            run("gh", "release", "download", tag, "--dir", directory, "--pattern", file.name)
            require(digest(Path(directory) / file.name) == digest(file), "Uploaded bytes differ: " + file.name)


def add_missing_public_assets(tag, files, published_assets):
    existing_names = {asset["name"] for asset in published_assets}
    existing_files = [file for file in files if file.name in existing_names]
    missing_files = [file for file in files if file.name not in existing_names]
    # Check existing bytes before any mutation, including when a previous upload was interrupted.
    verify_uploaded(tag, existing_files)
    if missing_files:
        run("gh", "release", "upload", tag, *(str(file) for file in missing_files))
        verify_uploaded(tag, missing_files)


def main():
    meta, files = validate()
    print("Validated release packages and checksums", flush=True)
    if "--validate-only" in os.sys.argv:
        return
    require(os.environ.get("GH_REPO") == "Zhgma/animeko", "Unexpected repository")
    tag = meta["tag"]
    prerelease = meta.get("prerelease", False)
    target = run("gh", "api", f"repos/Zhgma/animeko/git/ref/tags/{tag}", "--jq", ".object.sha").stdout.strip()
    require(target == meta["sourceCommit"], "Release tag must point at the source commit")
    existing = run("gh", "release", "view", tag, "--json", "isDraft,isPrerelease,assets", check=False)
    if existing.returncode == 0:
        state = json.loads(existing.stdout)
        if not state["isDraft"]:
            require(state["isPrerelease"] == prerelease, "Unexpected release channel")
            add_missing_public_assets(tag, files, state.get("assets", []))
            print("Verified public release; added missing assets without replacing existing bytes")
            return
    else:
        run("gh", "release", "create", tag, "--verify-tag", "--draft", "--title", f"Animecomic {tag}", "--notes-file", str(ROOT / "release-notes.md"), *( ["--prerelease"] if prerelease else []))
    run("gh", "release", "upload", tag, *(str(p) for p in files), "--clobber")
    verify_uploaded(tag, files)
    run("gh", "release", "edit", tag, "--draft=false",
        "--prerelease=" + str(prerelease).lower(), "--latest=" + str(not prerelease).lower())
    print(f"Published https://github.com/Zhgma/animeko/releases/tag/{tag}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        detail = str(error)
        if isinstance(error, subprocess.CalledProcessError):
            detail += "\n" + (error.stderr or error.stdout or "")
        annotation = detail.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print("::error::" + annotation, flush=True)
        raise
