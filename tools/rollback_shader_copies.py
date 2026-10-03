"""Remove only Electron's previous local shader copies; preserve original packs/settings."""
from pathlib import Path
import datetime, hashlib, json, re, shutil
import deploy_release

root = Path(__file__).resolve().parents[1]
instance = Path(json.loads(Path(__file__).with_name('deploy.local.json').read_text(encoding='utf-8'))['instance'])
deploy_release.check_world_locks(instance/'saves')
report = json.loads((root/'build/shader-compat-verification.json').read_text(encoding='utf-8'))
backup = instance/'electron-backups'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-shader-rollback')
backup.mkdir(parents=True, exist_ok=False)
replacements = {r['copy']:r['original'] for r in report if 'copy' in r}
config = instance/'config/iris.properties'
if config.exists():
    text = config.read_text(encoding='utf-8')
    match = re.search(r'^shaderPack=([^\r\n]*)',text,re.M)
    if match and match.group(1) in replacements:
        original = replacements[match.group(1)]
        assert (instance/'shaderpacks'/original).is_file()
        shutil.copy2(config,backup/config.name)
        config.write_text(text[:match.start(1)]+original+text[match.end(1):],encoding='utf-8')
moved=[]
for copy in replacements:
    for name in (copy,copy+'.txt'):
        p=instance/'shaderpacks'/name
        if p.exists():
            shutil.move(str(p),str(backup/name));moved.append(name)
originals={r['original']:hashlib.sha256((instance/'shaderpacks'/r['original']).read_bytes()).hexdigest()==r['original_sha256'] for r in report if 'original_sha256' in r}
assert all(originals.values()), originals
result={'moved_out_of_shaderpacks':moved,'original_hashes_unchanged':originals,'backup':str(backup)}
(root/'build/shader-rollback-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
