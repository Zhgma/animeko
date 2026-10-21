"""Package an already verified Windows release image for the push-triggered publisher."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

from publish import ROOT, digest, require, validate_windows_zip


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="Clean checkout of the release source commit")
    parser.add_argument("output", type=Path, help="Local artifact archive outside Git")
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    meta = json.loads((ROOT / "release.json").read_text(encoding="utf-8"))
    head = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    require(head == meta["sourceCommit"], "Checkout does not match release source")
    changes = subprocess.check_output(["git", "-C", str(source), "status", "--porcelain", "--untracked-files=no"], text=True)
    require(not changes.strip(), "Source checkout has uncommitted changes")
    image = source / "app/desktop/build/compose/binaries/main-release/app/Ani"
    require((image / "Ani.exe").is_file(), "Missing Release executable")
    # BuildConfig is the compiled application's identity, independent of the archive filename.
    identity_found = False
    for jar in (image / "app").glob("*.jar"):
        with zipfile.ZipFile(jar) as archive:
            name = "me/him188/ani/app/platform/AniBuildConfigDesktop.class"
            if name in archive.namelist():
                identity = archive.read(name)
                require(meta["version"].encode() in identity and head.encode() in identity, "Compiled app identity differs")
                identity_found = True
    require(identity_found, "Compiled BuildConfig missing")
    manifest = dict(version=meta["version"], sourceCommit=head, platform="windows", arch="x86_64", buildType="release")
    name = f"ani-{meta['version']}-windows-x86_64.zip"
    output.mkdir(parents=True, exist_ok=True)
    package = output / name
    require(not package.exists(), "Output already exists; verify it before choosing a fresh output directory")
    with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for file in sorted(image.rglob("*")):
            require(not file.is_symlink(), "Unexpected packaged symlink")
            if file.is_file():
                archive.write(file, file.relative_to(image.parent).as_posix())
        archive.writestr("Ani/release-manifest.json", json.dumps(manifest, indent=2) + "\n")
    validate_windows_zip(package, meta)
    info = dict(size=package.stat().st_size, sha256=digest(package), parts=[])
    with package.open("rb") as stream:
        index = 0
        while data := stream.read(48 * 1024 * 1024):
            part = ROOT / "assets" / f"{name}.part{index:03}"
            part.write_bytes(data)
            info["parts"].append(dict(name=part.name, size=len(data), sha256=hashlib.sha256(data).hexdigest()))
            index += 1
    meta["assets"][name] = info
    with package.open("rb") as stream:
        sha1 = hashlib.file_digest(stream, "sha1").hexdigest()
    for suffix, content in [(".sha1", sha1), (".sha256", info["sha256"] + "  " + name + "\n")]:
        checksum = ROOT / "assets" / (name + suffix)
        checksum.write_bytes(content.encode("ascii"))
        (output / checksum.name).write_bytes(checksum.read_bytes())
        meta["assets"][checksum.name] = dict(size=checksum.stat().st_size, sha256=digest(checksum))
    (ROOT / "release.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    (output / "release.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(archive=str(package), **info), indent=2), flush=True)


if __name__ == "__main__":
    main()
