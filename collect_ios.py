"""Collect a verified iOS build from the pinned source before publishing a release."""
import argparse
import json
import subprocess
import tempfile
import time
from pathlib import Path
from publish import ROOT, digest, require, validate_ipa


def gh_json(*args):
    return json.loads(subprocess.check_output(['gh', *args], text=True))


def check_run(run, config, meta):
    require(run['head_sha'] == meta['sourceCommit'], 'iOS workflow source differs')
    require(run['head_branch'] == 'build/ios-unsigned', 'Unexpected iOS branch')
    require(run['path'] == '.github/workflows/ios-unsigned.yml', 'Unexpected iOS workflow')
    require(run['repository']['full_name'] == 'Zhgma/animeko', 'Unexpected iOS repository')
    require(run['id'] == config['runId'], 'Unexpected iOS run')
    if run['status'] == 'completed':
        require(run['conclusion'] == 'success', 'iOS build did not succeed: ' + str(run['conclusion']))
        return True
    return False


def collect(meta, config):
    run_id=config['runId']
    deadline=time.monotonic()+config.get('waitMinutes', 155)*60
    while True:
        run=gh_json('api', f'repos/Zhgma/animeko/actions/runs/{run_id}')
        if check_run(run,config,meta): break
        require(time.monotonic()<deadline, 'Timed out waiting for iOS Release build')
        print(f'Waiting for iOS Release build {run_id}: {run["status"]}',flush=True)
        time.sleep(45)
    artifact_name='animecomic-ios-unsigned-'+meta['sourceCommit']
    with tempfile.TemporaryDirectory() as temp:
        subprocess.run(['gh','run','download',str(run_id),'--repo','Zhgma/animeko',
                        '--name',artifact_name,'--dir',temp],check=True)
        source=Path(temp)
        ipa_name=f'Animecomic-{meta["version"]}-ios-arm64-unsigned.ipa'
        items=[(ipa_name,ipa_name),('ipa-metadata.json','ios-build-info.json'),
               ('安装说明.txt','ios-installation.txt')]
        for original,name in items:
            data=(source/original).read_bytes()
            dest=ROOT/'assets'/name
            dest.write_bytes(data)
            meta['assets'][name]=dict(size=len(data),sha256=digest(dest))
        validate_ipa(ROOT/'assets'/ipa_name,meta)
    sums=ROOT/'assets/SHA256SUMS-all-platforms.txt'
    sums.write_text(''.join(f'{info["sha256"]}  {name}\n' for name,info in meta['assets'].items()
                           if name.endswith(('.apk','.zip','.ipa'))),encoding='ascii')
    meta['assets'][sums.name]=dict(size=sums.stat().st_size,sha256=digest(sums))
    (ROOT/'release.json').write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')
    print('Verified iOS IPA from pinned source; release manifest includes all three platforms',flush=True)


if __name__=='__main__':
    meta=json.loads((ROOT/'release.json').read_text(encoding='utf-8'))
    config=meta.get('iosBuild')
    if config: collect(meta,config)
    else: print('No external iOS build requested')
