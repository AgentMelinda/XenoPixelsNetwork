"""Regenerates the XenoAPI capability table from the native adapter source.

A method counts as unsupported when its body starts with `throw XenoApiAdapters.unsupported`.
Methods that are native but refuse for some entities (for example mob-only targeting) count as
native. Run from the repository root:  python scripts/xenoapi_capabilities.py
Prints Markdown; paste it into docs/native-xenoapi-adapters.md.
"""
import pathlib
import re

ROOT = pathlib.Path('src/main/java/net/bullettrain/xenopixelsmod/npc/script/api/xeno')
CONTRACTS = {
    'XenoEntityAdapter': 'IEntity', 'XenoLivingAdapter': 'IEntityLiving',
    'XenoNpcAdapter': 'ICustomNpc / IMob', 'XenoPlayerAdapter': 'IPlayer', 'XenoWorldAdapter': 'IWorld',
    'XenoItemAdapter': 'IItemStack', 'XenoNbtAdapter': 'INbt', 'XenoPosAdapter': 'IPos',
    'XenoDataAdapter': 'IData', 'XenoTimersAdapter': 'ITimers', 'XenoDamageSourceAdapter': 'IDamageSource',
    'XenoBlockAdapter': 'IBlock', 'XenoContainerAdapter': 'IContainer', 'NativeNpcApi': 'NpcAPI',
    'XenoFactionAdapter': 'IFaction', 'XenoFactionHandler': 'IFactionHandler', 'XenoQuestAdapter': 'IQuest',
    'XenoQuestCategory': 'IQuestCategory', 'XenoQuestHandler': 'IQuestHandler', 'XenoQuestObjective': 'IQuestObjective',
    'XenoDialogAdapter': 'IDialog', 'XenoDialogOption': 'IDialogOption', 'XenoDialogCategory': 'IDialogCategory',
    'XenoDialogHandler': 'IDialogHandler', 'XenoDialogAvailability': 'IAvailability', 'XenoCloneHandler': 'ICloneHandler',
    'XenoPlayerMail': 'IPlayerMail', 'XenoMark': 'IMark', 'XenoEntityItemAdapter': 'IEntityItem',
}
METHOD = re.compile(r'@Override\s+public\s+(?:synchronized\s+)?[^;{=]*?\b(\w+)\s*\(([^)]*)\)\s*\{')


def methods(source):
    for match in METHOD.finditer(source):
        name = match.group(1)
        if name in ('equals', 'hashCode', 'toString'):
            continue
        end, depth = match.end(), 1
        while depth:
            depth += (source[end] == '{') - (source[end] == '}')
            end += 1
        body = source[match.end():end - 1].strip()
        params = re.sub(r'\s+', ' ', match.group(2)).strip()
        types = ', '.join(p.strip().split(' ')[0] for p in params.split(',')) if params else ''
        yield f'{name}({types})', body.startswith('throw XenoApiAdapters.unsupported')


def main():
    rows, details, native_total, unsupported_total = [], [], 0, 0
    for cls, contract in CONTRACTS.items():
        found = list(methods((ROOT / f'{cls}.java').read_text(encoding='utf-8')))
        native = [m for m, u in found if not u]
        unsupported = [m for m, u in found if u]
        native_total += len(native)
        unsupported_total += len(unsupported)
        rows.append(f'| `{contract}` | {len(native)} | {len(unsupported)} |')
        details.append(f'### `{contract}`\n\nNative: ' + ', '.join(f'`{m}`' for m in native) + '.\n\nUnsupported: '
                       + (', '.join(f'`{m}`' for m in unsupported) if unsupported else 'none') + '.\n')
    print('| Contract | Native | Unsupported |\n| --- | ---: | ---: |')
    print('\n'.join(rows))
    print(f'| **Total** | **{native_total}** | **{unsupported_total}** |\n')
    print('\n'.join(details))


if __name__ == '__main__':
    main()
