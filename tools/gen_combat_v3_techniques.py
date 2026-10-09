"""Builds src/main/resources/data/xenopixelsmod/combat_v3/techniques.json from the reference catalog.

Run from the repository root:  python tools/gen_combat_v3_techniques.py

One definition per observed attack occurrence in docs/combat-v3/reference-catalog.json. Legacy
label-only ids are emitted as compatibility aliases to the first occurrence with that label. The
beat lists are archetype timelines, not per-attack choreography: every definition is written with
"animationStatus": "archetype_placeholder" until its own clip and timing have been authored and
compared against the source interval.
"""
import collections
import json
import re
import unicodedata
import copy
from pathlib import Path

CATALOG = 'docs/combat-v3/reference-catalog.json'
OUT = 'src/main/resources/data/xenopixelsmod/combat_v3/techniques.json'
CHOREOGRAPHY = 'docs/combat-v3/choreography.json'

NOT_ATTACKS = {'power_up', 'pose_buff', 'transformation', 'editor_cut', 'section_checkpoint',
               'ordinary_or_transition'}
# Labels that are stat buffs or poses in the game, wherever a researcher filed them.
BUFF_LABELS = {'al massimo', 'animo saiyan', 'carica alla max potenza', 'finto coraggio', 'gesto letale',
               'massima potenza', 'potenz. limite massimo', 'risveglio di majin', 'sono super vegeta!',
               'sono un guerr. di 1° cl.', 'super spirito caparbio', 'ultima energia', 'vattene!',
               'trasmissione istantanea', 'kaikosen', 'posa finale della giust…', 'arriva mister bu!',
               'ecco un regalo!', 'assalto!', 'addio, tenshinhan', 'scambio di corpi', 'kapa',
               'mi sto emozionando', 'dammi energia!', 'ora sono arrabbiato!'}

# Spelling variants of one on-screen label, as different researchers typed it.
ALIASES = {'raff. energ. max potenza': 'Raff. Ener. MAX Potenza', 'super onda esplosiva': 'Onda Super Esplosiva'}

# English names players see. Where the English release has a well-known name it is used; the
# rest are direct translations of the Italian label. Japanese names are kept as they are.
ENGLISH = {
    "Shin Gekir. Shin'ou'hou": "True Raging God Flash",
    "Barriera Chou Makouhou": "Super Demon Mouth Barrier",
    "Madan Esplosivo": "Explosive Flash Barrage",
    "Super Madan Esplosivo": "Super Explosive Flash Barrage",'Acido': 'Acid',
 'Artiglio Del Lupo': 'Wolf Fang Fist',
 'Artiglio Supr. Sc. Tart.': 'Supreme Turtle School Claw',
 'Artiglio del Drago': 'Dragon Claw',
 'Att. Cupola Infuocato': 'Heat Dome Attack',
 'Att. Spada Splendente': 'Shining Sword Attack',
 'Attacco Big Bang': 'Big Bang Attack',
 'Attacco Bruciante': 'Burning Attack',
 'Attacco Cometa Porpora': 'Purple Comet Attack',
 'Attacco Luminoso Finale': 'Final Shine Attack',
 'Attacco Mach Spaziale': 'Space Mach Attack',
 'Attacco di Kaiohken': 'Kaioken Attack',
 'Attacco di Meteoriti': 'Meteor Attack',
 'Bakuretsu Mahoko': 'Explosive Demon Light',
 'Bakuretsu Ranma': 'Explosive Wild Dance',
 'Barriera Chou Makouhou': 'Chou Makouhou Barrier',
 'Barriera Energ. Max Pot.': 'Full Power Energy Barrier',
 'Barriera Fiammeggiante': 'Blazing Barrier',
 'Barriera Missile Mortale': 'Deadly Missile Barrier',
 'Bomb. Renegade di Rekoom': 'Recoome Renegade Bomber',
 'Bomb. di Comb. di Rekoom': 'Recoome Fighting Bomber',
 'Bomba Regalo': 'Gift Bomb',
 'Bomba di Luce': 'Light Grenade',
 'Bomba di Saibaiman': 'Saibaman Bomb',
 'Cannone Anima Speciale': 'Special Soul Cannon',
 'Cannone Frant. dello Sp.': 'Spirit Breaking Cannon',
 'Cannone Galick': 'Galick Gun',
 'Cannone Garlic Finale': 'Final Galick Cannon',
 "Cannone dell'Aura": 'Tri-Beam',
 'Chou Makouhou': 'Super Demon Mouth Cannon',
 'Chou Makousen': 'Super Demon Flash',
 'Chou Maretsugeki': 'Super Violent Strike',
 'Ciambelle Galattiche': 'Galactic Donuts',
 'Colpo Nova': 'Nova Strike',
 'Combinaz. di Meteoriti': 'Meteor Combination',
 'Combinazione stravagante': 'Extravagant Combination',
 'Cronometro della Giust.': 'Justice Stopwatch',
 'Danza Sanguinaria': 'Bloody Dance',
 'Dinamite Galattica': 'Galaxy Dynamite',
 'Disco Volante Letale': 'Death Saucer',
 'Distruggi il pianeta!': 'Destroy The Planet!',
 'Distruttore Letale': 'Lethal Destroyer',
 'Distruttore massimo': 'Maximum Buster',
 'Distruzione Castigatrice': 'Punishing Blaster',
 'Dodonpa': 'Dodon Ray',
 'Eliminatore di Rekoom': 'Recoome Eraser Gun',
 'Esplosione Elegante': 'Elegant Blaster',
 'Esplosione Finale': 'Final Explosion',
 'Esplosione Solare': 'Solar Flare',
 'Esplosione Vulcanica': 'Volcano Explosion',
 'Fend. Ka-Blam Miracolo': 'Miracle Ka-Blam Slash',
 'Fendente Luminoso': 'Shining Slash',
 'Fendente Squarciante': 'Rending Slash',
 'Fendente della Giustizia': 'Justice Slash',
 'Folle Impeto': 'Mad Rush',
 'Frantumateste di Dodoria': 'Dodoria Head Breaker',
 'Frantumatore Bruciante': 'Burning Breaker',
 'Frantumatore polveriz…': 'Pulverizing Breaker',
 'Freccia Cadente': 'Shooting Star Arrow',
 'Freccia Luminosa': 'Light Arrow',
 'Fuochi del Male': 'Evil Flames',
 'Furia di Maiden!': "Maiden's Rage!",
 'Gekiretsu Madan': 'Raging Flash Barrage',
 'Gekiretsu Ranbu': 'Raging Dance',
 "Gekiretsu Shin'ou'hou": 'Raging God Flash',
 'Granata Infernale': 'Hellzone Grenade',
 'Grande Cannone Diramato': 'Great Scatter Cannon',
 'Impatto Finale': 'Final Impact',
 'Impatto Sorprendente': 'Surprise Impact',
 'Impatto Supremo': 'Supreme Impact',
 'Impeto Castigatore': 'Punishing Rush',
 'Impeto Dirompente': 'Bursting Rush',
 'Impeto Disperato': 'Desperate Rush',
 'Impeto ad Alta Velocita': 'High Speed Rush',
 'Impeto ad Alta Velocità': 'High Speed Rush',
 'Impeto della Giustizia': 'Justice Rush',
 'Kamehameha': 'Kamehameha',
 'Kamehameha Big Bang': 'Big Bang Kamehameha',
 'Kamehameha Big Bang x100': 'Big Bang Kamehameha x100',
 'Kamehameha Finale': 'Final Kamehameha',
 'Kamehameha Fratelli': 'Bros. Kamehameha',
 'Kamehameha Furiosa': 'Angry Kamehameha',
 'Kamehameha Istantanea': 'Instant Kamehameha',
 'Kamehameha Kaiohken x20': 'Kamehameha Kaioken x20',
 'Kamehameha Max Potenza': 'Full Power Kamehameha',
 'Kamehameha Originale': 'Original Kamehameha',
 'Kamehameha Padre-Figlio': 'Father-Son Kamehameha',
 'Kamehameha x10': 'Kamehameha x10',
 'Kienzan': 'Destructo Disc',
 'Lampo Finale': 'Final Flash',
 'Lampo Massimo': 'Maximum Flash',
 'Lampo Mistico': 'Mystic Flash',
 'Lampo e Assassinio': 'Flash And Assassination',
 'Madan Esplosivo': 'Explosive Madan',
 'Mankoku Kyoutenshou': 'Galaxy Dynasty Palm',
 'Masenko': 'Masenko',
 'Missile Gill': 'Gill Missile',
 'Morirai per mano mia!': "You'll Die By My Hand!",
 'Mossa Speciale di Guldo': 'Guldo Special',
 'Nuovo Triraggio': 'Neo Tri-Beam',
 'Onda Demoniaca Esplosiva': 'Explosive Demon Wave',
 'Onda Ener. MAX Potenza': 'Full Power Energy Wave',
 'Onda Energetica a espansione': 'Expanding Energy Wave',
 'Onda Esplosiva': 'Explosive Wave',
 'Onda Super Esplosiva': 'Super Explosive Wave',
 "Onda d'urto": 'Shock Wave',
 'Ora Piacere di Freezer': "Now It's Frieza's Pleasure",
 'Paralisi': 'Paralysis',
 'Penetra!': 'Penetrate!',
 'Pioggia Pietre Fisiche': 'Psychic Rock Rain',
 'Pioggia Pietre Giganti': 'Giant Rock Rain',
 'Psicocinesi': 'Psychokinesis',
 'Pugno Rovinoso Dinamico': 'Dynamic Mess-Em-Up Punch',
 'Questa volta muoio!': 'You Might Die This Time!',
 'Raff. Ener. MAX Potenza': 'Full Power Energy Barrage',
 'Raggio Diffuso': 'Scatter Beam',
 'Raggio Impazzito': 'Crazy Finger Beam',
 'Raggio Let. di Sbarram.': 'Death Beam Barrage',
 'Raggio Letale': 'Death Beam',
 'Raggio al Cioccolato': 'Chocolate Beam',
 'Ressa di Mostri': 'Monster Crush',
 'Ruggito': 'Roar',
 'S. At. Kamikaze Fantasma': 'Super Ghost Kamikaze Attack',
 'S. Raffica Onda Energ.': 'Super Energy Wave Volley',
 'Schianto della Meteorite': 'Meteor Crash',
 'Sentenza della Giustizia': 'Justice Judgment',
 'Sfera Ener. MAX Potenza': 'Full Power Energy Ball',
 'Sfera Frantumante': 'Crusher Ball',
 'Sfera Genkidama': 'Spirit Bomb',
 'Sfera Letale': 'Death Ball',
 "Shin Gekir. Shin'ou'hou": "Shin Gekiretsu Shin'ou'hou",
 'Soukidan': 'Spirit Ball',
 'Stritolatore Ka-Blam': 'Ka-Blam Crusher',
 'Super Cannone Garlic': 'Super Galick Gun',
 'Super Kamehameha': 'Super Kamehameha',
 'Super Madan Esplosivo': 'Super Explosive Madan',
 'Super Masenko': 'Super Masenko',
 'Super Sfera Genkidama': 'Super Spirit Bomb',
 'Supernova': 'Supernova',
 'Telecinesi': 'Telekinesis',
 'Tempesta Bruciante': 'Burning Storm',
 'Tempesta Mortale': 'Death Storm',
 'Tempesta Sfavillante': 'Sparkling Storm',
 'Ultra Palla Vol. Ene.': 'Ultra Energy Volleyball',
 'Urlo Malvagio': 'Evil Shout'}

PUNCH = 'combat.one_handed_punch_right'
KICK = 'combat.gutkick_right'


def plain(text):
    text = unicodedata.normalize('NFKD', text)
    return ''.join(c for c in text if not unicodedata.combining(c))


def slug(text):
    return re.sub(r'[^a-z0-9]+', '_', plain(text).lower()).strip('_')


def archetype(label, kinds):
    l = plain(label).lower()

    def has(*words):
        return any(w in l for w in words)

    if has('paralisi', 'psicocinesi', 'telecinesi', 'cronometro', 'esplosione solare', 'mossa speciale di guldo'):
        return 'hold'
    if has('kienzan', 'disco', 'ciambelle'):
        return 'disc'
    if has('raggio letale', 'dodonpa', 'raggio al cioccolato', 'freccia luminosa'):
        return 'laser'
    if has('raff', 'raggio impazzito', 'raggio let. di sbarram', 'pioggia', 'missile', 'madan', 'gekiretsu ranbu',
           'distruttore massimo', 'attacco luminoso', 's. raffica'):
        return 'volley'
    if has('genkidama', 'supernova', 'sfera letale', 'distruggi il pianeta', 'bomba di luce', 'colpo nova',
           'palla vol'):
        return 'giant_ball'
    if has('onda esplosiva', 'onda super esplosiva', 'super onda esplosiva', 'urlo', 'ruggito', 'tempesta',
           'barriera', 'esplosione finale', 'esplosione vulcanica', "onda d'urto", 'bomba di saibaiman',
           'onda energetica a espansione', 'onda demoniaca esplosiva', 'fuochi del male'):
        return 'radial'
    if has('kamehameha', 'cannone', 'onda ener', 'lampo', 'masenko', 'distruzione', 'esplosione elegante',
           'makou', 'mahoko', 'shin', 'nuovo triraggio', 'distruttore letale', 'fendente', 'chou maretsugeki'):
        return 'beam'
    if has('sfera', 'attacco big bang', 'attacco bruciante', 'soukidan', 'bomba', 'granata', 'freccia cadente',
           'acido', 'questa volta muoio', 'morirai per mano mia'):
        return 'ball'
    if has('ressa di mostri', 'stritolatore'):
        return 'grab'
    joined = ' '.join(kinds)
    if 'melee_energy' in joined:
        return 'melee_energy'
    if 'melee' in joined or 'grab' in joined:
        return 'melee'
    if 'beam' in joined:
        return 'beam'
    if 'volley' in joined:
        return 'volley'
    if 'radial' in joined or 'area' in joined or 'wave' in joined:
        return 'radial'
    if 'hold' in joined:
        return 'hold'
    if 'energy' in joined:
        return 'ball'
    return 'melee'


def ki_technique(label, kind):
    """Map BT3 label → DragonMineZ PredefinedTechniques id (colors + hitboxes).

    More-specific phrases must win before generic ones (e.g. \"kamehameha big bang\"
    before bare \"kamehameha\"). Radial/hold use DMZ natives for color registration and
    optional fire; melee/grab stay None.
    """
    l = plain(label).lower()
    # Melee/grab/radial/hold never carry kiTechnique (catalog: kiTechnique iff KI_RELEASE).
    # Radial still fires DMZ final_explosion from V3TechniqueRuntime RADIAL beat by type.
    if kind in ('melee', 'grab', 'radial', 'hold'):
        return None
    # Order matters: first match wins.
    table = [
        ('kamehameha big bang', 'big_bang'),
        ('big bang kamehameha', 'big_bang'),
        ('padre e figlio', 'kamehameha'),
        ('father-son', 'kamehameha'),
        ('bros', 'kamehameha'),
        ('fratelli', 'kamehameha'),
        ('kamehameha finale', 'kamehameha'),
        ('final kamehameha', 'kamehameha'),
        ('super kamehameha', 'kamehameha'),
        ('kamehameha', 'kamehameha'),
        ('galick', 'galick_gun'),
        ('garlic', 'galick_gun'),
        ('super masenko', 'masenko'),
        ('masenko', 'masenko'),
        ('chou maretsugeki', 'makkanko'),
        ('maretsugeki', 'makkanko'),
        ('violent strike', 'makkanko'),
        ('fendente', 'masenko'),  # yellow justice slash
        ('slash', 'masenko'),
        ('giustizia', 'masenko'),
        ('justice', 'masenko'),
        ('triraggio', 'makkanko'),
        ('tri-beam', 'makkanko'),
        ('tri beam', 'makkanko'),
        ("cannone dell'aura", 'makkanko'),
        ('nuovo triraggio', 'makkanko'),
        ('neo tri', 'makkanko'),
        ('anima speciale', 'soul_punisher'),
        ('special soul', 'soul_punisher'),
        ('soul cannon', 'soul_punisher'),
        ('soul punisher', 'soul_punisher'),
        ('frant. dello sp', 'makkanko'),
        ('spirit breaking', 'makkanko'),
        ('bakuretsu mahoko', 'final_flash'),
        ('explosive demon', 'final_flash'),
        ('distruttore letale', 'death_beam'),
        ('lethal destroyer', 'death_beam'),
        ('raggio diffuso', 'makkanko'),
        ('scatter beam', 'makkanko'),
        ('cannone diramato', 'makkanko'),
        ('scatter cannon', 'makkanko'),
        ('shin gekir', 'makkanko'),
        ("shin'ou", 'makkanko'),
        ('eliminatore di rekoom', 'final_flash'),
        ('eraser gun', 'final_flash'),
        ('esplosione elegante', 'burning_attack'),
        ('elegant blaster', 'burning_attack'),
        ('distruzione castigatrice', 'final_flash'),
        ('punishing blaster', 'final_flash'),
        ('onda ener', 'kamehameha'),  # full power energy wave ≈ blue blast
        ('energy wave', 'kamehameha'),
        ('lampo finale', 'final_flash'),
        ('final flash', 'final_flash'),
        ('lampo', 'final_flash'),
        ('attacco big bang', 'big_bang'),
        ('big bang', 'big_bang'),
        ('bruciante', 'burning_attack'),
        ('burning', 'burning_attack'),
        ('soukidan', 'sokidan'),
        ('sokidan', 'sokidan'),
        ('genkidama', 'spiritbomb'),
        ('spirit bomb', 'spiritbomb'),
        ('supernova', 'supernova'),
        ('makou', 'makkanko'),
        ('makanko', 'makkanko'),
        ('makkanko', 'makkanko'),
        ('dodonpa', 'death_beam'),
        ('raggio letale', 'death_beam'),
        ('death beam', 'death_beam'),
        ('kienzan', 'kienzan'),
        ('raff', 'ki_barrage'),
        ('barrage', 'ki_barrage'),
        ('madan', 'ki_barrage'),
        ('gekiretsu', 'ki_barrage'),
        # Solar Flare / Taiyoken blind is V3SolarFlare.applyBlind — not a KI_RELEASE projectile.
    ]
    for word, native in table:
        if word in l:
            return native
    return {
        'beam': 'kamehameha',
        'laser': 'death_beam',
        'disc': 'kienzan',
        'volley': 'ki_barrage',
        'giant_ball': 'supernova',
        'ball': 'big_bang',
        'melee_energy': 'sokidan',
    }.get(kind)


def beat(kind, tick, duration=0, payload='', value=0.0):
    return {'kind': kind, 'tick': tick, 'duration': duration, 'payload': payload, 'value': value}


TIMELINES = {
    'melee': (25.0, 160, lambda: [beat('POSE', 0, payload=PUNCH), beat('APPROACH', 2),
                                  beat('STRIKE', 6, value=0.5), beat('POSE', 9, payload=KICK),
                                  beat('STRIKE', 10, value=0.5), beat('POSE', 13, payload=PUNCH),
                                  beat('STRIKE', 14, value=0.5), beat('STRIKE', 18, value=0.5),
                                  beat('POSE', 22, payload=KICK), beat('STRIKE', 24, value=1.2),
                                  beat('SHOVE', 24, payload='FORWARD'), beat('END', 32)]),
    'grab': (25.0, 180, lambda: [beat('POSE', 0, payload=PUNCH), beat('APPROACH', 2),
                                 beat('HOLD_TARGET', 4, duration=14), beat('POSE', 16, payload=KICK),
                                 beat('STRIKE', 18, value=1.5), beat('SHOVE', 18, payload='FORWARD'),
                                 beat('END', 28)]),
    'melee_energy': (40.0, 220, lambda: [beat('POSE', 0, payload=PUNCH), beat('APPROACH', 2),
                                         beat('STRIKE', 6, value=0.5), beat('STRIKE', 10, value=0.5),
                                         beat('STRIKE', 14, value=0.7), beat('SHOVE', 14, payload='FORWARD'),
                                         beat('KI_CHARGE', 16), beat('KI_RELEASE', 26), beat('END', 44)]),
    'beam': (40.0, 200, lambda: [beat('KI_CHARGE', 2), beat('KI_RELEASE', 16), beat('END', 46)]),
    'laser': (20.0, 100, lambda: [beat('KI_CHARGE', 1), beat('KI_RELEASE', 6), beat('END', 22)]),
    'disc': (30.0, 140, lambda: [beat('KI_CHARGE', 2), beat('KI_RELEASE', 10), beat('END', 30)]),
    'ball': (30.0, 140, lambda: [beat('KI_CHARGE', 2), beat('KI_RELEASE', 12), beat('END', 34)]),
    'giant_ball': (80.0, 400, lambda: [beat('KI_CHARGE', 2), beat('KI_RELEASE', 26), beat('END', 56)]),
    'volley': (35.0, 180, lambda: [beat('KI_CHARGE', 2), beat('KI_RELEASE', 10), beat('END', 40)]),
    'radial': (30.0, 160, lambda: [beat('POSE', 0, payload=PUNCH), beat('RADIAL', 10, payload='7', value=1.4),
                                   beat('END', 24)]),
    'hold': (15.0, 240, lambda: [beat('POSE', 0, payload=PUNCH), beat('HOLD_TARGET', 6, duration=60),
                                 beat('END', 16)]),
}


def apply_choreography(techniques, authored):
    """Merge authored choreography, pinned to an observed occurrence; preserve costs and identity."""
    if authored.get('schema') != 1:
        raise ValueError('Unsupported choreography schema')
    result = copy.deepcopy(techniques)
    by_id = {entry['id']: entry for entry in result}
    seen = set()
    allowed = {'id', 'sourceStartsMs', 'animationStatus', 'durationTicks', 'beats', 'camera',
               'evidence', 'landmarks', 'unverified'}
    for entry in authored['entries']:
        key = entry['id']
        if key not in by_id or key in seen or set(entry) - allowed:
            raise ValueError(f'Unknown, duplicate, or invalid choreography: {key}')
        seen.add(key)
        baseline = by_id[key]
        if entry.get('sourceStartsMs') != baseline['sourceStartsMs']:
            raise ValueError(f'Stale choreography source start: {key}')
        for field in ('animationStatus', 'durationTicks', 'beats', 'camera'):
            if field in entry:
                baseline[field] = copy.deepcopy(entry[field])
    return result


def main():
    catalog = json.load(open(CATALOG, encoding='utf-8'))
    occurrences = []
    label_groups = collections.OrderedDict()
    for entry in catalog['entries']:
        label = entry.get('displayName')
        if not label or entry['kind'] in NOT_ATTACKS:
            continue
        label = re.sub(r'\s*\[.*?\]\s*$', '', label).strip()
        if plain(label).lower() in {plain(b).lower() for b in BUFF_LABELS}:
            continue
        label = ALIASES.get(plain(label).lower(), label)
        if entry.get('sourceStartMs') is None:
            raise ValueError(f"Attack occurrence has no source start: {entry.get('id')}")
        occurrence = {'id': entry['id'], 'name': label, 'kind': entry['kind'],
                      'start': entry['sourceStartMs']}
        occurrences.append(occurrence)
        label_groups.setdefault(slug(label), []).append(occurrence)

    techniques = []
    for occurrence in sorted(occurrences, key=lambda item: (item['start'], item['id'])):
        kind = archetype(occurrence['name'], [occurrence['kind']])
        cost, cooldown, beats = TIMELINES[kind]
        beats = beats()
        techniques.append({
            'id': occurrence['id'],
            'name': ENGLISH.get(occurrence['name'], occurrence['name']),
            'sourceLabel': occurrence['name'],
            'type': kind,
            'durationTicks': max(b['tick'] + b['duration'] for b in beats),
            'kiCost': cost,
            'cooldownTicks': cooldown,
            'kiTechnique': ki_technique(occurrence['name'], kind),
            'animationStatus': 'archetype_placeholder',
            'sourceStartsMs': [occurrence['start']],
            'beats': beats,
        })
    if Path(CHOREOGRAPHY).exists():
        techniques = apply_choreography(techniques, json.loads(Path(CHOREOGRAPHY).read_text(encoding='utf-8')))
    aliases = {
        'xenopixelsmod:bt3_' + key: sorted(group, key=lambda item: (item['start'], item['id']))[0]['id']
        for key, group in sorted(label_groups.items())
    }
    out = {'schema': 2, 'generatedFrom': CATALOG, 'compatibilityAliases': aliases,
           'techniques': techniques}
    import os
    tmp = OUT + '.tmp'
    with open(tmp, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write('\n')
    os.replace(tmp, OUT)
    print(len(techniques), 'techniques', len(aliases), 'compatibility aliases',
          dict(collections.Counter(t['type'] for t in techniques)))


if __name__ == '__main__':
    main()
