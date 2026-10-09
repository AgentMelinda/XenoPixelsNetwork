"""Generates the mod's Effekseer effects: textures, .efkproj projects and compiled .efkefc files.

Usage:
  python tools/effekseer/gen_effects.py [all|hakai|thruster|explosion|sparking|aura|aura2|aura3|aura4|ki ...] [--effekseer DIR] [--preview]

  --effekseer  folder holding Tool/bin/Effekseer.exe (1.80.x). Defaults to EFFEKSEER_HOME, then
               tools/new_particles/Effekseer1.80.6Win.
  --preview    after building, opens each effect in the editor, plays it and saves a frame strip
               to tools/effekseer/previews/<slot>.png (Windows; it clicks inside the editor).

Output: src/main/resources/assets/xenopixelsmod/effeks/<slot>/<slot>.efkefc plus its textures.
Editable sources: tools/effekseer/<set>/<slot>/<slot>.efkproj (open them in the editor).
Every value written is checked against what the editor saved back, so a misspelt element fails
the run instead of silently doing nothing.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from efkgen import build  # noqa: E402
from efkgen.effects import aura, aura2, aura3, aura4, explosion, hakai, ki, sparking, thruster  # noqa: E402

SETS = {'hakai': hakai, 'thruster': thruster, 'explosion': explosion, 'sparking': sparking, 'aura': aura,
        'aura2': aura2, 'aura3': aura3, 'aura4': aura4, 'ki': ki}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('sets', nargs='*', default=['all'], help='all, or any of: ' + ', '.join(SETS))
    ap.add_argument('--effekseer')
    ap.add_argument('--preview', action='store_true')
    ap.add_argument('--missing', action='store_true',
                    help='shared sets (aura, aura2, aura3, aura4): build only the effects not there yet')
    args = ap.parse_args(argv)
    names = list(SETS) if 'all' in args.sets else args.sets
    unknown = [n for n in names if n not in SETS]
    if unknown:
        sys.exit(f'unknown set(s): {", ".join(unknown)}; choose from {", ".join(SETS)}')
    editor = build.find_effekseer(args.effekseer)
    problems = []
    for name in names:
        effect_set = SETS[name]
        if hasattr(effect_set, 'FOLDER'):
            problems += (build.build_missing(effect_set, editor) if args.missing
                         else build.build_shared_set(effect_set, editor))
        else:
            problems += build.build_set(effect_set, editor)
    if problems:
        sys.exit('\n'.join(problems))
    if args.preview:
        for name in names:
            effect_set = SETS[name]
            for slot in effect_set.EFFECTS:
                opts = getattr(effect_set, 'PREVIEW_VIEW', {}).get(slot, {})
                print('preview', build.preview(slot, editor, **opts))


if __name__ == '__main__':
    main()
