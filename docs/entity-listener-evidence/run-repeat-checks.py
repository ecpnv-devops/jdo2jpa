from pathlib import Path
import concurrent.futures
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parent
ENV = os.environ.copy()
ENV['JAVA_HOME'] = '/Users/danhaywood/.sdkman/candidates/java/21.0.10-tem'
ENV['PATH'] = ENV['JAVA_HOME'] + '/bin:' + ENV['PATH']
ENV['MAVEN_OPTS'] = '-Xmx6g -Xss4m'
MODULES = json.loads((ROOT / 'repeat-modules.json').read_text())

def snapshot(app, module):
    names = set(subprocess.check_output(['git', 'ls-files', '--', module], cwd=app, text=True).splitlines())
    names.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', module], cwd=app, text=True).splitlines())
    return {name: hashlib.sha256((app / name).read_bytes()).hexdigest() if (app / name).is_file() else None for name in sorted(names)}

def run(side):
    app = ROOT / ('repeat-' + side)
    results = []
    for module in MODULES:
        before = snapshot(app, module)
        stages = []
        for profile in ['rewrite', 'rewrite_post', 'rewrite_local']:
            version = '1.2.4-f04-baseline' if side == 'baseline' else '1.2.4-f04-candidate6'
            cmd = ['mvn', '-o', '-Dmaven.repo.local=' + str(ROOT / 'repositories' / side),
                   '-Djdo2jpa.version=' + version, '-pl', module, '-P' + profile,
                   '-Ddisable_dn', '-DskipTests', 'rewrite:runNoFork']
            log = ROOT / ('repeat-' + side + '-' + module.replace('/', '_') + '-' + profile + '.log')
            with log.open('w') as output:
                process = subprocess.run(cmd, cwd=app, env=ENV, stdout=output, stderr=subprocess.STDOUT)
            text = log.read_text(errors='replace')
            errors = [line for line in text.splitlines() if '[ERROR]' in line or 'UnresolvedEntityHierarchyException' in line]
            stages.append({'command': cmd, 'exit': process.returncode, 'errors': errors[:10], 'log': str(log)})
            if process.returncode or errors:
                break
        after = snapshot(app, module)
        changed = [name for name in sorted(set(before) | set(after)) if before.get(name) != after.get(name)]
        results.append({'module': module, 'before': before, 'after': after, 'changedPaths': changed,
                        'stages': stages, 'stable': not changed and len(stages) == 3 and all(not s['exit'] and not s['errors'] for s in stages)})
        (ROOT / (side + '-repeat-results.json')).write_text(json.dumps(results, indent=2))
        print(side, module, 'stable=' + str(results[-1]['stable']), 'changed=' + str(len(changed)), flush=True)
    return results

with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    list(pool.map(run, ['baseline', 'candidate']))
