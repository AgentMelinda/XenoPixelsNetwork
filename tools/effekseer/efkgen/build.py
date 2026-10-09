"""Compile effect sets with the Effekseer editor (headless) and check what it kept."""
import os
import shutil
import subprocess
import sys
import zlib
import xml.etree.ElementTree as ET
from pathlib import Path

from .project import T, project

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from efkefc2xml import chunks, decode  # noqa: E402

REPO = Path(__file__).resolve().parents[3]
TOOLS = REPO / 'tools' / 'effekseer'
OUT = REPO / 'src' / 'main' / 'resources' / 'assets' / 'xenopixelsmod' / 'effeks'
BUNDLED_EDITOR = REPO / 'tools' / 'new_particles' / 'Effekseer1.80.6Win'

# Values the editor drops because they equal its defaults (checked by hand against the samples):
# 4 is the turbulence FieldScale default and 100 the Life default.
DEFAULTS = {'255', '0', '1', 'True', '360', '4', '100'}


def leaves(el, path=''):
    """(path, text) of every leaf; Node children are numbered so trees can be compared."""
    out = []
    counts = {}
    for c in el:
        i = counts.get(c.tag, 0); counts[c.tag] = i + 1
        p = f'{path}/{c.tag}[{i}]'
        if len(c):
            out += leaves(c, p)
        else:
            out.append((p, (c.text or '').strip()))
    return out


def same(a, b):
    try:
        return abs(float(a) - float(b)) < 1e-4
    except ValueError:
        return a.lower() == b.lower()


def check_saved(authored_xml, efkefc: Path):
    """Every value the editor kept must equal ours; values it dropped are returned."""
    saved = None
    for tag, data in chunks(efkefc.read_bytes()):
        if tag == b'EDIT':
            saved = ET.fromstring(decode(zlib.decompress(data)).split('\n', 1)[1])
    assert saved is not None, f'{efkefc} has no EDIT chunk'
    ours = dict(leaves(ET.fromstring(authored_xml.split('\n', 1)[1])))
    theirs = dict(leaves(saved))
    missing = []
    for p, v in ours.items():
        if p.split('/')[1] in ('ToolVersion[0]', 'Version[0]'):
            continue
        if p in theirs:
            assert same(theirs[p], v), f'{efkefc.name}: {p} saved as {theirs[p]!r}, wrote {v!r}'
        else:
            missing.append((p, v))
    return missing


def find_effekseer(arg):
    candidates = [arg] if arg else []
    candidates += [os.environ.get('EFFEKSEER_HOME'), str(BUNDLED_EDITOR)]
    for c in candidates:
        if c and (Path(c) / 'Tool' / 'bin' / 'Effekseer.exe').is_file():
            return Path(c)
    sys.exit('Effekseer editor not found: pass --effekseer <folder with Tool/bin/Effekseer.exe> '
             '(https://effekseer.github.io, 1.80.x) or set EFFEKSEER_HOME')


def build_set(effect_set, editor: Path):
    """Builds every slot of one set; returns the list of problems (empty when all is well).

    effect_set: a module with NAME, EFFECTS {slot: builder()}, PREVIEW {slot: frames} and
    textures(folder). Each slot is built in place in the resources folder (the editor stores
    texture paths relative to the output file); its editable source is copied to
    tools/effekseer/<NAME>/<slot>/.
    """
    exe = editor / 'Tool' / 'bin' / 'Effekseer.exe'
    problems = []
    for slot, build in effect_set.EFFECTS.items():
        dest = OUT / slot
        if dest.exists():
            shutil.rmtree(dest)
        effect_set.textures(dest / 'texture')
        root_node, procedural = build()
        xml = project(root_node, procedural, effect_set.PREVIEW[slot])
        proj = dest / f'{slot}.efkproj'
        proj.write_text(xml, encoding='utf-8')
        out = dest / f'{slot}.efkefc'
        subprocess.run([str(exe), '-cui', '-in', str(proj), '-o', str(out)], cwd=exe.parent,
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if not out.is_file():
            sys.exit(f'{slot}: the editor did not write {out}')
        used = {t.text for t in ET.fromstring(xml.split('\n', 1)[1]).iter()
                if t.text and t.text.startswith(T)}
        for tex in (dest / 'texture').iterdir():
            if T + tex.name not in used:
                tex.unlink()
        for p, v in check_saved(xml, out):
            if v not in DEFAULTS:
                problems.append(f'{slot}: {p} = {v} was not kept by the editor')
        work = TOOLS / effect_set.NAME / slot
        if work.exists():
            shutil.rmtree(work)
        shutil.copytree(dest, work)
        (work / f'{slot}.efkefc').unlink()
        proj.unlink()
        print(f'{slot}: {out.stat().st_size} bytes, {len(used)} textures')
    return problems


def build_shared_set(effect_set, editor: Path, workers=1):
    """Builds a set whose many effects share one folder and one texture set (the auras: two
    effects per colour). Output: effeks/<FOLDER>/<effect>.efkefc plus effeks/<FOLDER>/texture/.
    The editor runs one copy at a time (parallel runs fail), so they compile in turn; each is
    checked like build_set's.
    """
    from concurrent.futures import ThreadPoolExecutor

    exe = editor / 'Tool' / 'bin' / 'Effekseer.exe'
    dest = OUT / effect_set.FOLDER
    if dest.exists():
        shutil.rmtree(dest)
    effect_set.textures(dest / 'texture')
    jobs = []
    for name, build in effect_set.EFFECTS.items():
        root_node, procedural = build()
        xml = project(root_node, procedural, effect_set.PREVIEW.get(name, 60))
        proj = dest / f'{name}.efkproj'
        proj.write_text(xml, encoding='utf-8')
        jobs.append((name, xml, proj, dest / f'{name}.efkefc'))

    def compile_one(job):
        name, xml, proj, out = job
        subprocess.run([str(exe), '-cui', '-in', str(proj), '-o', str(out)], cwd=exe.parent,
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if not out.is_file():
            return [f'{name}: the editor did not write {out}']
        return [f'{name}: {p} = {v} was not kept by the editor'
                for p, v in check_saved(xml, out) if v not in DEFAULTS]

    problems = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for found in pool.map(compile_one, jobs):
            problems += found
    used = set()
    for _, xml, _, _ in jobs:
        used |= {t.text for t in ET.fromstring(xml.split('\n', 1)[1]).iter() if t.text and t.text.startswith(T)}
    for tex in (dest / 'texture').iterdir():
        if T + tex.name not in used:
            tex.unlink()
    work = TOOLS / effect_set.NAME
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    shutil.copytree(dest / 'texture', work / 'texture')
    for _, _, proj, _ in jobs:
        shutil.move(str(proj), str(work / proj.name))
    if hasattr(effect_set, 'extra_files'):
        effect_set.extra_files(dest)
    total = sum((dest / f'{n}.efkefc').stat().st_size for n, _, _, _ in jobs)
    print(f'{effect_set.NAME}: {len(jobs)} effects, {total} bytes, {len(used)} shared textures')
    return problems


def build_missing(effect_set, editor: Path):
    """Compiles only the effects of a shared set that are not in its folder yet, leaving the rest
    and the textures alone. For adding to a large set - a new brightness level, say - without the
    three quarters of an hour a full build_shared_set takes. Effects whose definition changed are
    NOT rebuilt by this; use the full build for that.
    """
    exe = editor / 'Tool' / 'bin' / 'Effekseer.exe'
    dest = OUT / effect_set.FOLDER
    work = TOOLS / effect_set.NAME
    if not (dest / 'texture').is_dir():
        sys.exit(f'{effect_set.NAME}: nothing built yet, run the full build first')
    work.mkdir(parents=True, exist_ok=True)
    problems = []
    built = 0
    for name, build in effect_set.EFFECTS.items():
        out = dest / f'{name}.efkefc'
        if out.is_file():
            continue
        root_node, procedural = build()
        xml = project(root_node, procedural, effect_set.PREVIEW.get(name, 60))
        proj = dest / f'{name}.efkproj'
        proj.write_text(xml, encoding='utf-8')
        subprocess.run([str(exe), '-cui', '-in', str(proj), '-o', str(out)], cwd=exe.parent,
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if not out.is_file():
            problems.append(f'{name}: the editor did not write {out}')
            continue
        problems += [f'{name}: {p} = {v} was not kept by the editor'
                     for p, v in check_saved(xml, out) if v not in DEFAULTS]
        shutil.move(str(proj), str(work / proj.name))
        built += 1
    if hasattr(effect_set, 'extra_files'):
        effect_set.extra_files(dest)
    print(f'{effect_set.NAME}: {built} missing effects built')
    return problems


def preview(slot, editor: Path, zoom=12, frames=3, gap_ms=250, start_ms=300):
    """Opens the compiled effect in the editor, plays it and saves a frame strip (Windows only)."""
    script = TOOLS / 'preview.ps1'
    out = TOOLS / 'previews'
    out.mkdir(exist_ok=True)
    result = subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
                    '-Editor', str(editor / 'Tool' / 'Effekseer.exe'),
                    '-Effect', str(OUT / slot / f'{slot}.efkefc'),
                    '-Out', str(out / f'{slot}.png'),
                    '-Zoom', str(zoom), '-Frames', str(frames), '-GapMs', str(gap_ms),
                    '-StartMs', str(start_ms)], check=False)
    return out / f'{slot}.png' if result.returncode == 0 else f'skipped (editor not in front): {slot}'
