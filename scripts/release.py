#!/usr/bin/env python3
"""Release guards and APK validation, used locally and by GitHub Actions."""
import argparse, hashlib, json, os, pathlib, re, subprocess
ROOT = pathlib.Path(__file__).resolve().parents[1]
REPO = 'milankablar/car-karaoke'
PACKAGE = 'io.github.milankablar.carkaraoke'
def run(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()
def version():
    props = dict(line.split('=', 1) for line in (ROOT / 'version.properties').read_text().splitlines() if '=' in line)
    name, code = props['versionName'], int(props['versionCode'])
    assert re.fullmatch(r'\d+\.\d+\.\d+', name), 'Use a semantic version such as 0.1.0'
    assert code > 0
    return name, code

def guard(tag):
    name, code = version()
    assert tag == f'car-karaoke-v{name}', 'Tag must match version.properties'
    run('git', 'fetch', 'origin', 'main')
    run('git', 'merge-base', '--is-ancestor', 'HEAD', 'origin/main')
    releases = json.loads(run('gh', 'api', f'repos/{REPO}/releases', '--paginate', '--slurp'))
    for release in [item for page in releases for item in page]:
        assert release['tag_name'] != tag, 'Release already exists; assets are immutable'
        if not release['tag_name'].startswith('car-karaoke-v'): continue
        previous = release['tag_name'].removeprefix('car-karaoke-v')
        assert tuple(map(int, name.split('.'))) > tuple(map(int, previous.split('.'))), 'Version must increase'
        metadata = next((a for a in release['assets'] if a['name'] == 'release-metadata.json'), None)
        assert metadata, 'Previous release metadata missing'
        raw = run('gh', 'api', f"repos/{REPO}/releases/assets/{metadata['id']}", '-H', 'Accept: application/octet-stream')
        assert code > json.loads(raw)['versionCode'], 'versionCode must increase'

def package():
    name, code = version()
    sdk = pathlib.Path(os.environ['ANDROID_HOME'])
    build_tools = sdk / 'build-tools' / '35.0.0'
    apk = ROOT / 'app/build/outputs/apk/release/app-release.apk'
    assert apk.exists(), 'Signed release APK missing'
    verification = run(str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk))
    fingerprint = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', verification).group(1)
    expected = (ROOT / 'distribution/signing-certificate.sha256').read_text().strip().lower().replace(':', '')
    assert fingerprint == expected, 'Unexpected signing certificate'
    badging = run(str(build_tools / 'aapt'), 'dump', 'badging', str(apk))
    assert f"name='{PACKAGE}'" in badging and f"versionCode='{code}'" in badging and f"versionName='{name}'" in badging
    assert 'application-debuggable' not in badging, 'Debug APK must never be released'
    out = ROOT / 'dist'; out.mkdir(exist_ok=True)
    for stale in out.glob('car-karaoke-*.apk'): stale.unlink()
    target = out / f'car-karaoke-{name}.apk'; target.write_bytes(apk.read_bytes())
    assert list(out.glob('*.apk')) == [target], 'Unexpected APK in distribution directory'
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (out / 'SHA256SUMS').write_text(f'{digest}  {target.name}\n')
    (out / 'release-metadata.json').write_text(json.dumps(dict(packageId=PACKAGE, versionName=name, versionCode=code, certificateSha256=fingerprint, apkSha256=digest, commit=run('git', 'rev-parse', 'HEAD')), indent=2)+'\n')
    print(f'Validated {target.name}: versionCode {code}, expected signing certificate')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['guard', 'package', 'tag']); args = parser.parse_args()
    tag = f'car-karaoke-v{version()[0]}'
    if args.action == 'package': package()
    elif args.action == 'guard': guard(os.environ.get('GITHUB_REF_NAME', tag))
    else:
        assert not run('git', 'status', '--porcelain'), 'Commit changes first'
        assert run('git', 'branch', '--show-current') == 'main', 'Release from main'
        guard(tag)
        run('git', 'tag', '-a', tag, '-m', f'Car Karaoke {version()[0]}')
        run('git', 'push', 'origin', tag)
        print(f'Pushed {tag}. Follow the Release workflow: https://github.com/{REPO}/actions')
