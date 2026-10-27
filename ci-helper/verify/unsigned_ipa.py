"""Validate a device Release IPA and collect it for user-side signing."""
import hashlib
import json
import plistlib
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path


def verify_ipa(ipa, output):
    properties = dict(line.strip().split("=", 1) for line in Path("gradle.properties").read_text().splitlines()
                      if "=" in line and not line.lstrip().startswith("#"))
    version = properties["version.name"]
    assert "-beta." in version, "This workflow produces beta packages"
    with zipfile.ZipFile(ipa) as archive:
        assert archive.testzip() is None, "Corrupt IPA"
        names = archive.namelist()
        app = "Payload/Animeko.app/"
        info = plistlib.loads(archive.read(app + "Info.plist"))
        assert info["CFBundleIdentifier"] == "org.animeko.animeko"
        assert info["CFBundleShortVersionString"] == properties["package.version"]
        assert info["CFBundleVersion"] == properties["ios.version.code"]
        assert info["CFBundleSupportedPlatforms"] == ["iPhoneOS"], "Simulator app is not installable on a device"
        assert not any(n.endswith("embedded.mobileprovision") for n in names), "Must not embed a signing profile"
        binary = archive.read(app + info["CFBundleExecutable"])
        magic, cpu = struct.unpack_from("<II", binary)
        assert magic == 0xFEEDFACF and cpu == 0x0100000C, "Expected a device arm64 Mach-O executable"
        for framework in ["application", "MediampFFmpegKit"]:
            assert app + f"Frameworks/{framework}.framework/{framework}" in names, f"Missing {framework}"
    output.mkdir(parents=True, exist_ok=True)
    destination = output / f"Animecomic-{version}-ios-arm64-unsigned.ipa"
    shutil.copy2(ipa, destination)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text(f"{digest}  {destination.name}\n")
    metadata = {"version": version, "bundle_id": info["CFBundleIdentifier"],
                "build": info["CFBundleVersion"], "minimum_ios": info["MinimumOSVersion"],
                "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
                "sha256": digest, "size": destination.stat().st_size,
                "signing": "ad-hoc placeholder only; requires user signing before installation"}
    (output / "ipa-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    (output / "安装说明.txt").write_text(
        f"Animecomic {version}，iPhone/iPad ARM64，要求 iOS {info['MinimumOSVersion']} 或更新版本。\n"
        "此包使用 Release 配置编译，没有开发者证书或设备描述文件。\n"
        "包内仅有不依赖苹果账号的临时签名；安装前需由用户通过爱思助手等工具重新签名。\n"
        "重新签名及安装效果需要在实际设备上验证。\n", encoding="utf-8")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    verify_ipa(Path(sys.argv[1]), Path(sys.argv[2]))
