#!/usr/bin/env python3
"""Generate a reproducible Obtainium config and web-safe onboarding link."""
import json, pathlib, re, sys, urllib.parse
root = pathlib.Path(__file__).resolve().parents[1]
config = {
 'id': 'io.github.milankablar.carkaraoke',
 'url': 'https://github.com/milankablar/car-karaoke',
 'author': 'milankablar', 'name': 'Car Karaoke',
 'additionalSettings': {
  'includePrereleases': False, 'fallbackToOlderReleases': True,
  'apkFilterRegEx': r'^car-karaoke-[0-9]+\.[0-9]+\.[0-9]+\.apk$',
  'filterReleaseTitlesByRegEx': r'^Car Karaoke [0-9]+\.[0-9]+\.[0-9]+$',
  'versionExtractionRegEx': r'[0-9]+\.[0-9]+\.[0-9]+',
  'matchGroupToUse': '$0', 'versionDetection': True
 }
}
settings = config['additionalSettings']
config['additionalSettings'] = json.dumps(settings, separators=(',', ':'))
link = 'https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/' + urllib.parse.quote(json.dumps(config, separators=(',', ':')), safe='')
files = {'distribution/obtainium.json': json.dumps(config, indent=2)+'\n', 'distribution/obtainium-link.txt': link+'\n'}
for name, content in files.items():
 path = root / name
 if '--check' in sys.argv: assert path.read_text() == content, f'Regenerate {name}'
 else: path.write_text(content)
assert re.fullmatch(settings['apkFilterRegEx'], 'car-karaoke-0.1.0.apk')
assert not re.fullmatch(settings['apkFilterRegEx'], 'app-debug.apk')
print('Obtainium config verified' if '--check' in sys.argv else link)
