import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path('/tmp/f04-acceptance-401d6c4/round6')
ENV = os.environ.copy()
ENV['JAVA_HOME'] = '/Users/danhaywood/.sdkman/candidates/java/21.0.10-tem'
ENV['PATH'] = ENV['JAVA_HOME'] + '/bin:' + ENV['PATH']
ENV['MAVEN_OPTS'] = '-Xmx6g -Xss4m'
REWRITE_ORDER = json.loads((ROOT / 'reactor-order.json').read_text())
JPA_ORDER = json.loads((ROOT / 'reactor-order-jpa.json').read_text())
ORDER = JPA_ORDER + [module for module in REWRITE_ORDER if module not in JPA_ORDER]
ERROR = re.compile(r'UnresolvedEntityHierarchyException|Cannot resolve .+ while processing .+ in com\.ecpnv\.')


def run(side):
    cwd = ROOT / ('estatio-' + side)
    logs = ROOT / (side + '-migration-logs')
    logs.mkdir(exist_ok=True)
    state = {'side': side, 'status': 'running', 'stages': [], 'completedModules': []}
    statefile = ROOT / (side + '-migration-state.json')
    if statefile.exists():
        state = json.loads(statefile.read_text())
        if state['status'] != 'ready to resume; failed module sources restored':
            return 'Refusing to reuse partial output without an explicit source-reset checkpoint'
        state['status'] = 'running'

    def save():
        temporary = statefile.with_suffix('.tmp')
        temporary.write_text(json.dumps(state, indent=2))
        temporary.replace(statefile)

    def execute(module, label, goals):
        cmd = ['mvn', '-o', '-Dmaven.repo.local=' + str(ROOT / 'repositories' / side),
               '-Djdo2jpa.version=' + ('1.2.4-f04-baseline' if side == 'baseline' else '1.2.4-f04-candidate6'), '-pl', module,
               '-Ddisable_dn', '-DskipTests'] + goals
        logfile = logs / (module.replace('/', '_').replace('.', 'root') + '-' + label + '.log')
        state['currentModule'] = module
        state['currentStage'] = label
        save()
        with logfile.open('w') as output:
            process = subprocess.run(cmd, cwd=cwd, env=ENV, stdout=output, stderr=subprocess.STDOUT)
        text = logfile.read_text(errors='replace')
        hierarchy_error = bool(ERROR.search(text))
        record = {'module': module, 'stage': label, 'command': cmd, 'exit': process.returncode,
                  'hierarchyError': hierarchy_error, 'log': str(logfile),
                  'errors': [line for line in text.splitlines() if '[ERROR]' in line][:25]}
        if label == 'jpa-install' and not process.returncode and not hierarchy_error and not record['errors']:
            record['installedArtifacts'] = [
                {'path': name, 'sha256': hashlib.sha256(Path(name).read_bytes()).hexdigest()}
                for name in re.findall(r'Installing .+ to (.+\.(?:jar|pom))$', text, re.M)
                if Path(name).is_file()
            ]
        state['stages'].append(record)
        if process.returncode or hierarchy_error or record['errors']:
            state['status'] = 'blocked; partial output rejected'
            save()
            return False
        save()
        return True

    for module in ORDER:
        if module in state['completedModules']:
            continue
        source = cwd / module / 'src'
        targets = module in REWRITE_ORDER and source.exists() and any(p.suffix == '.java' or p.name.endswith('.layout.xml') for p in source.rglob('*') if p.is_file())
        if targets:
            for profile in ['rewrite', 'rewrite_post', 'rewrite_local']:
                if not execute(module, profile, ['-P' + profile, 'rewrite:runNoFork']):
                    return state['status']
        if module in JPA_ORDER and not execute(module, 'jpa-install', ['-Pjpa,jpa-apt,!jdo,!jdo-apt', 'clean', 'install']):
            return state['status']
        state['completedModules'].append(module)
        save()
    state['status'] = 'regeneration and parent-first installation complete; diff review still required'
    save()
    return state['status']


with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    for side, result in zip(['baseline', 'candidate'], pool.map(run, ['baseline', 'candidate'])):
        print(side, result, flush=True)
