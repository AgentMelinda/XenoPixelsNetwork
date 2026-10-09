"""Builds only the Hakai effects. Kept so the documented command still works; the code lives in
efkgen/effects/hakai.py and the general tool is gen_effects.py.

Usage:
  python tools/effekseer/gen_hakai_effects.py [--effekseer <dir with Tool/bin/Effekseer.exe>] [--preview]
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_effects  # noqa: E402

if __name__ == '__main__':
    gen_effects.main(['hakai'] + sys.argv[1:])
