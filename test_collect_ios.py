import unittest
from collect_ios import check_run

class PinnedIosBuildTest(unittest.TestCase):
 def fixture(self):
  return dict(id=1,head_sha='a'*40,head_branch='build/ios-unsigned',path='.github/workflows/ios-unsigned.yml',repository={'full_name':'Zhgma/animeko'},status='completed',conclusion='success')
 def test_only_success_from_exact_source_is_accepted(self):
  self.assertTrue(check_run(self.fixture(),{'runId':1},{'sourceCommit':'a'*40}))
  for key,value in [('head_sha','b'*40),('head_branch','other'),('path','other.yml'),('id',2),('conclusion','failure')]:
   with self.subTest(key=key),self.assertRaises(ValueError):
    check_run(dict(self.fixture(),**{key:value}),{'runId':1},{'sourceCommit':'a'*40})
 def test_running_build_waits(self):
  self.assertFalse(check_run(dict(self.fixture(),status='in_progress',conclusion=None),{'runId':1},{'sourceCommit':'a'*40}))
