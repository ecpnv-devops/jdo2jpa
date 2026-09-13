import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            value.update(chunk)
    return value.hexdigest()


states = {side: json.loads((ROOT / (side + '-migration-state.json')).read_text())
          for side in ['baseline', 'candidate']}
completed = set(states['baseline']['completedModules']) & set(states['candidate']['completedModules'])
modules = sorted(set(json.loads((ROOT / 'reactor-order.json').read_text()) +
                     json.loads((ROOT / 'reactor-order-jpa.json').read_text())), key=len, reverse=True)
artifacts = []
inventories = {}
for side, state in states.items():
    app = ROOT / ('estatio-' + side)
    files = set(subprocess.check_output(['git', 'ls-files'], cwd=app, text=True).splitlines())
    files.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=app, text=True).splitlines())
    inventory = {}
    for name in sorted(files):
        path = app / name
        if path.is_file():
            inventory[name] = digest(path)
        elif not path.exists():
            inventory[name] = None
    inventories[side] = inventory
    for stage in state['stages']:
        if stage['stage'] != 'jpa-install' or stage['exit'] or stage['errors']:
            continue
        if 'installedArtifacts' in stage:
            artifacts.extend({'side': side, 'module': stage['module'], **artifact,
                              'installationLog': stage['log']} for artifact in stage['installedArtifacts'])
            continue
        text = Path(stage['log']).read_text(errors='replace')
        for name in re.findall(r'Installing .+ to (.+\.(?:jar|pom))$', text, re.M):
            path = Path(name)
            artifacts.append({'side': side, 'module': stage['module'], 'path': name,
                              'sha256': digest(path) if path.is_file() else None,
                              'installationLog': stage['log']})

changes = []
for name in sorted(set(inventories['baseline']) | set(inventories['candidate'])):
    owner = next((module for module in modules if module != '.' and name.startswith(module + '/')), '.')
    if owner in completed and inventories['baseline'].get(name) != inventories['candidate'].get(name):
        changes.append({'path': name, 'module': owner,
                        'baselineSha256': inventories['baseline'].get(name),
                        'candidateSha256': inventories['candidate'].get(name)})

result = {'complete': all('regeneration and parent-first installation complete' in state['status'] for state in states.values()),
          'completedCommonModules': sorted(completed), 'differencesInCompletedModules': changes,
          'note': 'Source inventories exclude ignored build products; installed parent artifacts are separately hashed. In-progress modules are not accepted output.'}
(ROOT / 'source-inventories.json').write_text(json.dumps(inventories, indent=2))
(ROOT / 'installed-parent-artifacts.json').write_text(json.dumps(artifacts, indent=2))
(ROOT / 'comparison-inventory-status.json').write_text(json.dumps(result, indent=2))
print(json.dumps({'complete': result['complete'], 'completedCommonModules': len(completed),
                  'differences': len(changes), 'parentArtifacts': len(artifacts),
                  'changedPaths': [change['path'] for change in changes]}, indent=2))
