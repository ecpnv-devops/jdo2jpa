import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent
ENV = os.environ.copy()
ENV['JAVA_HOME'] = '/Users/danhaywood/.sdkman/candidates/java/21.0.10-tem'
ENV['PATH'] = ENV['JAVA_HOME'] + '/bin:' + ENV['PATH']
subprocess.run(['javac', str(ROOT / 'InspectEntity.java')], env=ENV, check=True)
subprocess.run(['python3', str(ROOT / 'capture-evidence.py')], check=True, stdout=subprocess.DEVNULL)
inventory = json.loads((ROOT / 'comparison-inventory-status.json').read_text())
changes = inventory['differencesInCompletedModules']
modules = sorted({c['module'] for c in changes if c['path'].endswith('.java')})
commands = []
metadata = {}
for side in ['baseline', 'candidate']:
    app = ROOT / ('estatio-' + side)
    metadata[side] = {}
    for module in modules:
        stem = side + '-' + module.replace('/', '_')
        cp = ROOT / ('inventory-' + stem + '.classpath')
        log = ROOT / ('inventory-' + stem + '.log')
        cmd = ['mvn', '-o', '-Dmaven.repo.local=' + str(ROOT / 'repositories' / side),
               '-pl', module, '-Pjpa,jpa-apt,!jdo,!jdo-apt', 'dependency:build-classpath',
               '-Dmdep.outputFile=' + str(cp)]
        with log.open('w') as output:
            result = subprocess.run(cmd, cwd=app, env=ENV, stdout=output, stderr=subprocess.STDOUT)
        commands.append({'command': cmd, 'exit': result.returncode, 'log': str(log)})
        if result.returncode:
            raise RuntimeError('Cannot establish module classpath: ' + str(log))
        fullcp = ROOT / ('inventory-' + stem + '-full.classpath')
        fullcp.write_text(os.pathsep.join([str(app / module / 'target/test-classes'),
                                         str(app / module / 'target/classes'), cp.read_text().strip()]))
        names = []
        for change in changes:
            if change['module'] != module or not change['path'].endswith('.java'):
                continue
            source = app / change['path']
            match = re.search(r'^package\s+([\w.]+)\s*;', source.read_text(), re.M)
            if match:
                names.append(match.group(1) + '.' + source.stem)
        cmd = ['java', '-cp', str(ROOT), 'InspectEntity', str(fullcp)] + names
        result = subprocess.run(cmd, env=ENV, capture_output=True, text=True)
        output = ROOT / ('inventory-' + stem + '.jsonl')
        output.write_text(result.stdout)
        commands.append({'command': cmd, 'exit': result.returncode, 'output': str(output), 'stderr': result.stderr})
        for line in result.stdout.splitlines():
            row = json.loads(line)
            metadata[side][row['class']] = row

rows = []
for change in changes:
    path = change['path']
    a = (ROOT / 'estatio-baseline' / path).read_text()
    b = (ROOT / 'estatio-candidate' / path).read_text()
    # Only classify simple, explicitly permitted non-semantic differences automatically.
    def header_and_body(source):
        lines = source.splitlines(True)
        indices = [i for i, line in enumerate(lines) if re.match(r'^import [\w.*]+;\s*$', line)]
        end = max(indices) + 1 if indices else 0
        imports = {lines[i].strip() for i in indices}
        package = re.search(r'^package\s+([\w.]+);', source, re.M)
        return imports, ''.join(lines[end:]).lstrip(), package.group(1) if package else None
    ai, ab, ap = header_and_body(a)
    bi, bb, bp = header_and_body(b)
    changed_imports_unused = all('*' not in line and not re.search(r'\b' + re.escape(line[:-1].rsplit('.', 1)[-1]) + r'\b', ab)
                                 for line in ai ^ bi)
    category = 'unused imports/import-group whitespace' if ab == bb and ap == bp and changed_imports_unused else 'requires semantic review'
    package = re.search(r'^package\s+([\w.]+)\s*;', a, re.M)
    prefix = package.group(1) + '.' + Path(path).stem if package else None
    types = sorted({k for side in metadata.values() for k in side if prefix and (k == prefix or k.startswith(prefix + '$'))})
    type_rows = [{'class': k, 'baseline': metadata['baseline'].get(k), 'candidate': metadata['candidate'].get(k)} for k in types]
    if category == 'requires semantic review' and types and all(
            row['baseline'] and row['candidate'] and 'error' not in row['baseline'] and 'error' not in row['candidate']
            and {k: v for k, v in row['baseline'].items() if k != 'declaresEntityScan'} ==
                {k: v for k, v in row['candidate'].items() if k != 'declaresEntityScan'}
            and row['candidate']['declaresEntityScan'] == row['candidate']['declaresComponentScan']
            for row in type_rows):
        # Require the actual source delta to contain only EntityScan declarations/whitespace.
        strip_scan = lambda s: re.sub(r'^[ \t]*@EntityScan\([^\n]*\)\r?\n', '', s, flags=re.M)
        if strip_scan(a) == strip_scan(b):
            category = 'declared ComponentScan scope correction'
    rows.append({**change, 'classification': category, 'types': type_rows})
result = {'complete': inventory['complete'], 'completedCommonModules': len(inventory['completedCommonModules']),
          'scope': 'Completed-module differences only. Compiled annotation metadata, not runtime or XML/default-listener acceptance.',
          'changes': rows, 'commands': commands}
(ROOT / 'affected-class-inventory.json').write_text(json.dumps(result, indent=2))
print(json.dumps({'complete': result['complete'], 'completedCommonModules': result['completedCommonModules'],
                  'changes': [{k: row[k] for k in ['path', 'classification']} for row in rows],
                  'metadataErrors': [row for side in metadata.values() for row in side.values() if 'error' in row]}, indent=2))
