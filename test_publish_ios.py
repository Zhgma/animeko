import unittest
from unittest.mock import patch

import publish_ios as publisher


VERSION = "0.3.2-beta.5"
SOURCE = "a" * 40
DRAFT = {"tag_name": VERSION, "draft": True, "prerelease": True,
         "target_commitish": SOURCE, "assets": []}
TAG = {"object": {"sha": SOURCE}}


class PublicationTests(unittest.TestCase):
    def test_finds_draft_on_later_list_page(self):
        with patch.object(publisher, "api", side_effect=[
            [{"tag_name": "older"}] * 100, [DRAFT]
        ]) as api:
            self.assertEqual(publisher.find_release(VERSION), DRAFT)
            self.assertEqual(api.call_args_list[1].args, ("releases?per_page=100&page=2",))

    def test_resumes_draft_and_creates_deferred_tag_without_duplicate_release(self):
        with patch.object(publisher, "find_release", return_value=DRAFT), \
                patch.object(publisher, "optional_api", return_value=None), \
                patch.object(publisher, "api", return_value=TAG), \
                patch.object(publisher, "run") as run:
            self.assertEqual(publisher.prepare_release(VERSION, SOURCE), DRAFT)
            run.assert_called_once_with(
                "gh", "api", "repos/Zhgma/animeko/git/refs", "--method", "POST",
                "-f", f"ref=refs/tags/{VERSION}", "-f", f"sha={SOURCE}")

    def test_new_release_is_read_from_list_after_creation(self):
        with patch.object(publisher, "find_release", side_effect=[None, DRAFT]) as find, \
                patch.object(publisher, "optional_api", return_value=TAG), \
                patch.object(publisher, "api", return_value=TAG), \
                patch.object(publisher, "run") as run:
            self.assertEqual(publisher.prepare_release(VERSION, SOURCE), DRAFT)
            self.assertEqual(find.call_count, 2)
            self.assertEqual(run.call_args.args[:4], ("gh", "release", "create", VERSION))

    def test_published_beta_is_reused_without_mutations(self):
        release = dict(DRAFT, draft=False)
        with patch.object(publisher, "find_release", return_value=release), \
                patch.object(publisher, "optional_api", return_value=TAG), \
                patch.object(publisher, "api", return_value=TAG), \
                patch.object(publisher, "run") as run:
            self.assertEqual(publisher.prepare_release(VERSION, SOURCE), release)
            run.assert_not_called()

    def test_rejects_wrong_source_or_stable_before_mutation(self):
        for release, tag in [
            (dict(DRAFT, prerelease=False), TAG),
            (dict(DRAFT, target_commitish="b" * 40), TAG),
            (DRAFT, {"object": {"sha": "b" * 40}}),
        ]:
            with self.subTest(release=release, tag=tag), \
                    patch.object(publisher, "find_release", return_value=release), \
                    patch.object(publisher, "optional_api", return_value=tag), \
                    patch.object(publisher, "run") as run:
                with self.assertRaises(ValueError):
                    publisher.prepare_release(VERSION, SOURCE)
                run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
