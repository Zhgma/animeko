import json
import os
import subprocess
import unittest
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

if __name__ == "__main__": unittest.main()
