"""Install a verified local release. Never overwrite a player's world or leave duplicate Electron JARs."""
import argparse
import hashlib
import json
import os
import shutil
import uuid
import zipfile
from datetime import datetime
from pathlib import Path
from minecraft_nbt import assert_no_dropped_items

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_world_locks(saves):
    if os.name != 'nt':
        raise RuntimeError('This installer currently validates Windows world locks only.')
    import msvcrt
    for lock in saves.glob('*/session.lock'):
        try:
            with lock.open('r+b') as stream:
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
                stream.seek(0)
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
        except OSError as error:
            raise RuntimeError(f'Close Minecraft before installing; world lock is in use: {lock}') from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance', type=Path)
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    config = ROOT/'tools/deploy.local.json'
    instance = args.instance or Path(json.loads(config.read_text(encoding='utf-8'))['instance'])
    instance = instance.resolve(strict=True)
    mods, saves = instance/'mods', instance/'saves'
    if not mods.is_dir() or not saves.is_dir():
        raise RuntimeError('Expected an existing Minecraft instance with mods and saves directories.')
    version = dict(line.split('=', 1) for line in (ROOT/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
    release = ROOT/f'dist/circuit-foundry-{version}'
    report = json.loads((release/'verification.json').read_text(encoding='utf-8'))
    jar = release/f'electron-{version}.jar'
    archive = ROOT/f'dist/circuit-academy-{version}.zip'
    if report['main']['version'] != version or report['map']['version'] != version:
        raise RuntimeError('Stale release report.')
    if sha(jar) != report['main']['sha256'] or sha(archive) != report['map']['sha256']:
        raise RuntimeError('Release hash mismatch; rerun release verification.')
    if report['map'].get('dropped_item_entities') != 0 or report['map'].get('chest_stacks') != 397 or not report['map'].get('components_above_bench') or report['map'].get('powered_pumps') != 2 or report['map'].get('processing_machines') != 4 or report['map'].get('pcb_stages') != 5 or not report['map'].get('pcb_control_working') or not report['map'].get('decimal_input_changes_verified') or not report['map'].get('pcb_assembly_working') or not report['map'].get('single_board_calculator') or not report['map'].get('pixel_stream_verified') or not report['map'].get('sequential_sample'):
        raise RuntimeError('Map has not passed the drop/inventory audit.')
    old_mods = []
    for existing in mods.glob('*.jar'):
        with zipfile.ZipFile(existing) as zipped:
            if 'fabric.mod.json' in zipped.namelist():
                # Some installed Fabric mods use literal newlines in metadata strings.
                metadata = json.loads(zipped.read('fabric.mod.json'), strict=False)
                if metadata.get('id') == 'electron':
                    old_mods.append(existing)
    stamp = datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    world = saves/f'Circuit_Foundry_Academy_{version}'
    if world.exists():
        world = saves/f'Circuit_Foundry_Academy_{version}_{stamp}'
    check_world_locks(saves)
    plan = {'version': version, 'instance': str(instance), 'jar': str(mods/jar.name),
            'world': str(world), 'previous_jars': [p.name for p in old_mods], 'dry_run': args.dry_run}
    if args.dry_run:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return
    backup = instance/'electron-backups'/stamp
    stage = saves/f'.electron-install-{uuid.uuid4().hex}'
    stage.mkdir()
    # Select only map entries, and reject traversal before writing any archive path.
    with zipfile.ZipFile(archive) as zipped:
        if zipped.testzip() is not None:
            raise RuntimeError('Map archive CRC check failed.')
        for info in zipped.infolist():
            if not info.filename.startswith('Circuit_Foundry_Academy/') or info.is_dir():
                continue
            relative = Path(info.filename.removeprefix('Circuit_Foundry_Academy/'))
            target = (stage/relative).resolve()
            if not target.is_relative_to(stage.resolve()):
                raise RuntimeError('Unsafe map archive path.')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(zipped.read(info))
    if not (stage/'level.dat').is_file():
        raise RuntimeError('Archive does not contain a playable world.')
    assert_no_dropped_items(stage)
    check_world_locks(saves)
    pending_jar = mods/(jar.name+'.pending')
    shutil.copy2(jar, pending_jar)
    if sha(pending_jar) != report['main']['sha256']:
        raise RuntimeError('Copied JAR failed verification.')
    backup.mkdir(parents=True)
    moved = []
    installed = False
    try:
        for previous in old_mods:
            previous.rename(backup/previous.name)
            moved.append(previous)
        pending_jar.rename(mods/jar.name)
        installed = True
        stage.rename(world)
    except Exception:
        if installed:
            (mods/jar.name).rename(backup/(jar.name+'.failed-install'))
        for previous in moved:
            (backup/previous.name).rename(previous)
        raise
    # Keep instructions and archives together beside the playable mod and save.
    published = instance/'electron-releases'/f'{version}-{stamp}'
    published.mkdir(parents=True)
    for file in [archive, ROOT/f'dist/circuit-foundry-{version}.zip', release/'verification.json']:
        shutil.copy2(file, published/file.name)
    for file in (ROOT/f'dist/circuit-academy-{version}').iterdir():
        if file.is_file():
            shutil.copy2(file, published/file.name)
    plan.update(backup=str(backup), archive_directory=str(published), jar_sha256=sha(mods/jar.name),
                dropped_item_entities=0, previous_worlds_overwritten=False)
    (published/'installation.json').write_text(json.dumps(plan, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    (ROOT/'build/last-installation.json').write_text(json.dumps(plan, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(plan, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
