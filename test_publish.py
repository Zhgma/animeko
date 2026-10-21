import json
import os
import subprocess
import tempfile
import unittest
import zipfile
import struct
from pathlib import Path
from unittest.mock import patch
import publish

class PublicationChannelTest(unittest.TestCase):
    def test_debug_publication_is_rejected_even_when_renamed(self):
        for assets in [
            {"ani-0.3.0-debug.apk": {"package": "me.him188.ani"}},
            {"ani-0.3.0.apk": {"package": "me.him188.ani.debug2"}},
            {"ani-0.3.0.apk": {"package": "me.him188.ani"}, "ani-debug.apk.sha1": {}},
            {},
        ]:
            with self.subTest(assets=assets), self.assertRaises(ValueError):
                publish.validate_public_assets({"assets": assets})

    def test_release_only_publication_is_accepted(self):
        publish.validate_public_assets({"assets": {"ani-0.3.0.apk": {"package": "me.him188.ani"}}})

    def exercise(self, prerelease, existing=None):
        calls = []
        tag = "0.2.0-beta.1" if prerelease else "0.2.0"
        meta = dict(tag=tag, prerelease=prerelease, sourceCommit="a" * 40)
        def run(*args, **kwargs):
            calls.append(args)
            if args[1] == "api":
                return subprocess.CompletedProcess(args, 0, "a" * 40)
            if args[1:3] == ("release", "view"):
                return subprocess.CompletedProcess(args, 1 if existing is None else 0, json.dumps(existing))
            return subprocess.CompletedProcess(args, 0, "")
        with patch.object(publish, "validate", return_value=(meta, [])), patch.object(publish, "run", side_effect=run), patch.object(publish, "verify_uploaded") as verify, patch.dict(os.environ, GH_REPO="Zhgma/animeko"):
            publish.main()
            verify.assert_called_once()
        return calls

    def test_beta_never_becomes_latest_stable(self):
        calls = self.exercise(True)
        create = next(c for c in calls if c[1:3] == ("release", "create"))
        self.assertIn("--prerelease", create)
        edit = calls[-1]
        self.assertIn("--prerelease=true", edit)
        self.assertIn("--latest=false", edit)

    def test_stable_remains_latest(self):
        edit = self.exercise(False)[-1]
        self.assertIn("--prerelease=false", edit)
        self.assertIn("--latest=true", edit)

    def test_existing_beta_is_verified_without_mutation(self):
        calls = self.exercise(True, dict(isDraft=False, isPrerelease=True))
        self.assertFalse(any(c[1:3] in (("release", "upload"), ("release", "edit")) for c in calls))

    def test_wrong_public_channel_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unexpected release channel"):
            self.exercise(True, dict(isDraft=False, isPrerelease=False))


class DesktopPublicationTest(unittest.TestCase):
    def test_missing_platform_is_added_without_overwriting_android(self):
        apk, desktop = Path("android.apk"), Path("windows.zip")
        with patch.object(publish, "verify_uploaded") as verify, patch.object(publish, "run") as run:
            publish.add_missing_public_assets("0.2.0-beta.11", [apk, desktop], [{"name": apk.name}])
            self.assertEqual(verify.call_args_list[0].args, ("0.2.0-beta.11", [apk]))
            self.assertEqual(verify.call_args_list[1].args, ("0.2.0-beta.11", [desktop]))
            run.assert_called_once_with("gh", "release", "upload", "0.2.0-beta.11", "windows.zip")

    def test_conflicting_existing_bytes_block_all_uploads(self):
        with patch.object(publish, "verify_uploaded", side_effect=ValueError("Uploaded bytes differ")), patch.object(publish, "run") as run:
            with self.assertRaisesRegex(ValueError, "Uploaded bytes differ"):
                publish.add_missing_public_assets("tag", [Path("android.apk"), Path("windows.zip")], [{"name": "android.apk"}])
            run.assert_not_called()

    def test_retry_does_not_upload_existing_desktop_again(self):
        files = [Path("windows.zip"), Path("windows.zip.sha1")]
        with patch.object(publish, "verify_uploaded") as verify, patch.object(publish, "run") as run:
            publish.add_missing_public_assets("tag", files, [{"name": p.name} for p in files])
            run.assert_not_called()
            verify.assert_called_once_with("tag", files)

    def test_split_package_round_trip_and_corruption(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(publish, "ROOT", Path(directory)):
            assets = Path(directory) / "assets"
            assets.mkdir()
            parts = []
            for index, data in enumerate([b"first", b"second"]):
                part = assets / f"windows.zip.part{index:03}"
                part.write_bytes(data)
                parts.append(dict(name=part.name, size=len(data), sha256=publish.digest(part)))
            self.assertEqual(publish.asset_path("windows.zip", {"parts": parts}).read_bytes(), b"firstsecond")
            part.write_bytes(b"bad")
            with self.assertRaisesRegex(ValueError, "Part bytes differ"):
                publish.asset_path("windows.zip", {"parts": parts})

    def test_parts_cannot_traverse_directories(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(publish, "ROOT", Path(directory)):
            with self.assertRaisesRegex(ValueError, "Unsafe part name"):
                publish.asset_path("windows.zip", {"parts": [dict(name="../secret", size=1, sha256="x")]})

    def test_windows_archive_layout_version_architecture_and_launcher(self):
        meta = dict(version="0.2.0-beta.11", sourceCommit="a" * 40)
        manifest = dict(**meta, platform="windows", arch="x86_64", buildType="release")
        exe = bytearray(100)
        exe[:2] = b"MZ"
        struct.pack_into("<I", exe, 60, 64)
        exe[64:68] = b"PE\0\0"
        struct.pack_into("<H", exe, 68, 0x8664)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "windows.zip"
            def create(config="[JavaOptions]", marker=manifest, launcher=exe, extra=None):
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("Ani/Ani.exe", launcher)
                    archive.writestr("Ani/app/Ani.cfg", config)
                    for name in ["java.dll", "jcef.dll", "jcef_helper.exe"]:
                        archive.writestr("Ani/runtime/bin/" + name, b"runtime")
                    archive.writestr("Ani/release-manifest.json", json.dumps(marker))
                    if extra: archive.writestr(extra, b"bad")
            create()
            publish.validate_windows_zip(path, meta)
            for arguments, error in [
                ({"marker": dict(manifest, version="0.1.0")}, "manifest differs"),
                ({"config": "-Dani.debug=true"}, "Development Windows"),
                ({"config": "-javaagent:input-agent.jar"}, "Development Windows"),
                ({"launcher": b"invalid"}, "Invalid Windows"),
                ({"extra": "../outside"}, "Unsafe ZIP"),
            ]:
                with self.subTest(arguments=arguments):
                    create(**arguments)
                    with self.assertRaisesRegex(ValueError, error):
                        publish.validate_windows_zip(path, meta)

if __name__ == "__main__": unittest.main()
