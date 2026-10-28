"""Publish verified IPA bytes from a completed build without compiling the app."""
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile


def run(*args):
    return subprocess.check_output(args, text=True, encoding="utf-8").strip()


def api(path):
    return json.loads(run("gh", "api", f"repos/Zhgma/animeko/{path}"))


def optional_api(path):
    result = subprocess.run(["gh", "api", f"repos/Zhgma/animeko/{path}"],
                            capture_output=True, text=True, encoding="utf-8")
    if result.returncode == 0:
        return json.loads(result.stdout)
    try:
        error = json.loads(result.stdout)
    except ValueError:
        error = {}
    if str(error.get("status")) == "404":
        return None
    raise RuntimeError(result.stderr or result.stdout)


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def find_release(version):
    # The tag endpoint excludes drafts; authenticated release lists include them.
    matches = []
    page = 1
    while True:
        releases = api(f"releases?per_page=100&page={page}")
        matches.extend(release for release in releases if release["tag_name"] == version)
        if len(releases) < 100:
            break
        page += 1
    require(len(matches) <= 1, "Multiple releases use the requested tag")
    return matches[0] if matches else None


def prepare_release(version, source):
    existing = find_release(version)
    if existing is not None:
        require(existing["prerelease"], "Refusing to modify a stable release")
        if existing["draft"]:
            require(existing["target_commitish"] == source, "Draft source differs")
    tag_path = f"git/ref/tags/{version}"
    tag = optional_api(tag_path)
    require(tag is None or tag["object"]["sha"] == source,
            "Release tag already points to another source")
    if tag is None:
        require(existing is None or existing["draft"], "Published release has no source tag")
        # Draft creation can defer tag creation until publication.
        run("gh", "api", "repos/Zhgma/animeko/git/refs", "--method", "POST",
            "-f", f"ref=refs/tags/{version}", "-f", f"sha={source}")
    require(api(tag_path)["object"]["sha"] == source, "Release source differs")
    if existing is None:
        run("gh", "release", "create", version, "--verify-tag", "--target", source,
            "--draft", "--prerelease", "--title", f"Animecomic {version} · iOS",
            "--notes-file", "release-notes.md")
        existing = find_release(version)
        require(existing is not None and existing["draft"] and existing["prerelease"],
                "Expected draft prerelease was not created")
    return existing


def main():
    require(os.environ.get("GH_REPO") == "Zhgma/animeko", "Unexpected repository")
    config = json.loads(Path("ipa-release.json").read_text())
    version, source = config["version"], config["source_sha"]
    require(re.fullmatch(r"0\.\d+\.\d+-beta\.[1-9]\d*", version), "Expected a beta release")
    require(re.fullmatch(r"[0-9a-f]{40}", source), "Invalid source commit")
    build = api(f"actions/runs/{config['run_id']}")
    require(build["status"] == "completed" and build["conclusion"] == "success", "Build has not succeeded")
    require(build["path"] == ".github/workflows/ios-unsigned.yml", "Unexpected build workflow")
    require(build["head_sha"] == source and build["head_branch"] == "build/ios-unsigned", "Unexpected build source")
    stable_before = api("releases/latest")["tag_name"]
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        incoming = root / "incoming"
        run("gh", "run", "download", str(config["run_id"]), "--name",
            f"animecomic-ios-unsigned-{source}", "--dir", str(incoming))
        metadata = json.loads((incoming / "ipa-metadata.json").read_text())
        require(metadata["version"] == version and metadata["source_sha"] == source, "IPA provenance mismatch")
        require(metadata["build"] == config["build"], "IPA build number mismatch")
        ipa = incoming / f"Animecomic-{version}-ios-arm64-unsigned.ipa"
        require(ipa.stat().st_size == metadata["size"] and digest(ipa) == metadata["sha256"], "IPA bytes mismatch")
        with zipfile.ZipFile(ipa) as archive:
            require(archive.testzip() is None, "Corrupt IPA")
            require(not any(name.endswith("embedded.mobileprovision") for name in archive.namelist()), "Unexpected signing profile")
            prefix = "Payload/Animeko.app/"
            info = plistlib.loads(archive.read(prefix + "Info.plist"))
            require(info["CFBundleIdentifier"] == "org.animeko.animeko", "Unexpected app")
            require(info["CFBundleVersion"] == config["build"] and info["CFBundleShortVersionString"] == version.split("-")[0], "App version mismatch")
            require(info["CFBundleSupportedPlatforms"] == ["iPhoneOS"], "Not a device app")
            require(info["MinimumOSVersion"] == "16.0", "Unexpected deployment target")
            require(struct.unpack_from("<II", archive.read(prefix + info["CFBundleExecutable"])) == (0xFEEDFACF, 0x0100000C), "Not ARM64")
            for framework in ("application", "MediampFFmpegKit"):
                require(prefix + f"Frameworks/{framework}.framework/{framework}" in archive.namelist(), "Missing framework")
        outgoing = root / "outgoing"
        outgoing.mkdir()
        shutil.copy2(ipa, outgoing / ipa.name)
        shutil.copy2(incoming / "ipa-metadata.json", outgoing / "ios-build-info.json")
        shutil.copy2(incoming / "安装说明.txt", outgoing / "ios-installation.txt")
        files = sorted(outgoing.iterdir())
        checksums = outgoing / "SHA256SUMS-ios.txt"
        checksums.write_text("".join(f"{digest(file)}  {file.name}\n" for file in files))
        files.append(checksums)
        existing = prepare_release(version, source)
        existing_names = {asset["name"] for asset in existing["assets"]}
        verify = root / "verified"
        verify.mkdir()
        for file in files:
            if file.name not in existing_names:
                run("gh", "release", "upload", version, str(file))
            run("gh", "release", "download", version, "--pattern", file.name, "--dir", str(verify))
            require(digest(verify / file.name) == digest(file), "Published bytes mismatch: " + file.name)
        if existing["draft"]:
            run("gh", "release", "edit", version, "--draft=false", "--prerelease=true", "--latest=false")
        published = api(f"releases/tags/{version}")
        require(not published["draft"] and published["prerelease"], "Prerelease is not public")
        require(api("releases/latest")["tag_name"] == stable_before, "Stable Latest changed")
        print(f"Published verified IPA: https://github.com/Zhgma/animeko/releases/tag/{version}")


if __name__ == "__main__":
    main()
