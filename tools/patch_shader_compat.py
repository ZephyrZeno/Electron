"""Create local copies of installed shader packs with state-aware Electron light mappings.

Never redistributes third-party shaders and never edits their original ZIPs. Mapping format:
https://shaders.properties/current/reference/miscellaneous/block_properties/
"""
from pathlib import Path
import argparse, datetime, hashlib, json, re, shutil, zipfile

LIGHTS=['electron:engineering_lamp:broken=false:power='+','.join(map(str,range(1,16))),
        'electron:indicator_led:broken=false:power='+','.join(map(str,range(1,16)))]
LIGHTS += ['electron:'+name+':powered=true' for name in ['and_gate','or_gate','not_gate','xor_gate','nand_gate','nor_gate','xnor_gate','buffer_gate','binary_memory']]
SUFFIX=' - Electron Lights'

def patch_properties(text):
    """Append to existing sea-lantern classifications, including preprocessor branches.

    Do not add duplicate property assignments: later assignments can replace earlier lists.
    Handle continued lines without moving a block into a different shader preprocessor branch.
    """
    lines=text.splitlines(keepends=True);out=[];changed=0;i=0
    while i<len(lines):
        part=[lines[i]];i+=1
        while part[-1].rstrip().endswith('\\') and i<len(lines):part.append(lines[i]);i+=1
        logical=' '.join(x.rstrip().removesuffix('\\') for x in part)
        if re.match(r'\s*block\.\d+\s*=',logical) and re.search(r'(?:^|\s)(?:minecraft:)?sea_lantern(?:\s|$)',logical.split('=',1)[1].strip()):
            missing=[x for x in LIGHTS if x not in logical]
            if missing:
                ending='\r\n' if part[-1].endswith('\r\n') else '\n'
                part[-1]=part[-1].rstrip()+' '+' '.join(missing)+ending;changed+=1
        out.extend(part)
    return ''.join(out),changed

def run(instance,activate=False):
    folder=instance/'shaderpacks';report=[];replacements={}
    for original in sorted(folder.glob('*.zip')):
        if SUFFIX in original.stem:continue
        with zipfile.ZipFile(original) as src:
            properties=[n for n in src.namelist() if n.endswith('shaders/block.properties')]
            changes={}
            for name in properties:
                text,n=patch_properties(src.read(name).decode('utf-8-sig'))
                if n:changes[name]=text.encode('utf-8')
            if not changes:
                report.append({'original':original.name,'status':'unsupported_no_known_light_map'});continue
            target=original.with_name(original.stem+SUFFIX+'.zip');temporary=target.with_suffix('.zip.tmp')
            with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as dst:
                for entry in src.infolist():dst.writestr(entry,changes.get(entry.filename,src.read(entry.filename)))
            with zipfile.ZipFile(temporary) as checked:
                assert checked.testzip() is None
                for name,data in changes.items():assert checked.read(name)==data and all(x in data.decode('utf-8') for x in LIGHTS)
            temporary.replace(target)
            option=Path(str(original)+'.txt')
            if option.exists() and not Path(str(target)+'.txt').exists():shutil.copy2(option,Path(str(target)+'.txt'))
            replacements[original.name]=target.name
            report.append({'original':original.name,'copy':target.name,'lights':len(LIGHTS),'original_sha256':hashlib.sha256(original.read_bytes()).hexdigest(),'status':'mapped_not_visual_verified'})
    config=instance/'config/iris.properties'
    if activate and config.exists():
        text=config.read_text(encoding='utf-8');match=re.search(r'^shaderPack=(.*)$',text,re.M)
        if match and match.group(1).strip() in replacements:
            backup=instance/'electron-backups'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-shader')
            backup.mkdir(parents=True,exist_ok=False);shutil.copy2(config,backup/config.name)
            text=text[:match.start(1)]+replacements[match.group(1).strip()]+text[match.end(1):]
            config.write_text(text,encoding='utf-8')
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--instance',type=Path);parser.add_argument('--activate',action='store_true');args=parser.parse_args()
    instance=args.instance or Path(json.loads(Path(__file__).with_name('deploy.local.json').read_text(encoding='utf-8'))['instance'])
    # Reuse release deployment's running-game protection before any changes.
    import deploy_release
    deploy_release.check_world_locks(instance/'saves')
    report=run(instance,args.activate)
    Path('build/shader-compat-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
