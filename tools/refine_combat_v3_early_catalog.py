"""Adds the inspected 0-449 second occurrence rows to the Combat V3 reference catalog."""

import json
from pathlib import Path


CATALOG = Path("docs/combat-v3/reference-catalog.json")
SOURCE = "video-dense-part1/early-gap-checkpoint-2026-10-08.md (all 1 Hz sheets inspected)"
EVIDENCE = "sampled_1hz_full_sheet_inspected_pending_source_pts"
PREFIX = "xenopixelsmod:bt3_early_"


ROWS = [
    (0, 11000, "opening", None, "editor_cut", "Creator graphics and black transition."),
    (11000, 15000, "kid_goku", "Finto Coraggio", "pose_buff", "Raised arm and crossed-upper-body support pose; pink aura expands without contact."),
    (15000, 22000, "kid_goku", "Kamehameha", "energy_beam", "Side/hip two-hand blue charge, forward wave and six-hit result."),
    (22000, 26000, "kid_goku", None, "power_up", "Blue charge grows into the Max-power state."),
    (26000, 40000, "kid_goku", "Penetra!", "cinematic_melee_energy", "Close-up dive and body hit launch the victim; attacker raises a blue orb and descends into the finishing impact."),
    (40000, 43000, "kid_goku", None, "ordinary_energy", "Unlabelled small yellow shots."),
    (43000, 44000, "goku_early", None, "editor_cut", "Match cut to adult Goku versus scouter Vegeta."),
    (44000, 53000, "goku_early", "Attacco di Kaiohken", "cinematic_melee", "Red-aura rush with successive aerial body impacts and victim displacement."),
    (53000, 60000, "goku_early", None, "ordinary_or_transition", "Charge, forward flight and close ready repositioning."),
    (60000, 64000, "goku_early", "Kamehameha", "energy_beam", "Two-hand blue charge and forward six-hit wave."),
    (64000, 69000, "goku_early", None, "ordinary_melee", "Unlabelled approach and close movement."),
    (69000, 72000, "goku_early", "Esplosione Solare", "energy_hold", "Front crouch/arm spread with a bright radial flash; victim reaction follows."),
    (72000, 77000, "goku_early", None, "power_up", "Blue charge, Max ring and ready repositioning."),
    (77000, 79000, "goku_early", "Kamehameha", "energy_beam", "Second standard blue wave occurrence."),
    (79000, 84000, "goku_early", None, "power_up", "Blue charge grows to Max-power state."),
    (84000, 99000, "goku_early", "Sfera Genkidama", "energy_projectile", "Overhead-to-palm gathering, distant victim blast, expanding white-blue sphere and recovery."),
    (99000, 106000, "goku_torn_shirt", "Kamehameha Kaiohken x20", "energy_beam", "Red Kaiohken aura remains around the attacker during the two-hand blue wave."),
    (106000, 111000, "goku_torn_shirt", "Mi sto emozionando", "pose_buff", "Circular ground wave and body glow during a stationary support pose."),
    (111000, 116000, "goku_torn_shirt", "Dammi Energia!", "pose_buff", "Both arms raise overhead while white-blue energy descends into the attacker."),
    (116000, 127000, "goku_torn_shirt", "Combinaz. di Meteoriti", "cinematic_melee", "Cinematic rush, alternating blows, aerial displacement, close-up strike and whiteout."),
    (127000, 132000, "goku_torn_shirt", "Kamehameha Kaiohken x20", "energy_beam", "Second red-aura two-hand blue wave occurrence."),
    (132000, 139000, "goku_torn_shirt", "Dammi Energia!", "pose_buff", "Repeated overhead energy-gathering support sequence."),
    (139000, 142000, "goku_torn_shirt", None, "power_up", "Blue charge grows to Max-power ring."),
    (142000, 150000, "goku_torn_shirt", "Sfera Genkidama", "energy_projectile", "Overhead gathering, blue sky rays, downward delivery and target whiteout."),
    (150000, 151000, "goku_super_saiyan", None, "editor_cut", "Match cut to Super Saiyan Goku on volcanic terrain."),
    (151000, 156000, "goku_super_saiyan", "Super Kamehameha", "energy_beam", "Compact torso charge and large blue-white six-hit wave."),
    (156000, 160000, "goku_super_saiyan", "Ora sono arrabbiato!", "pose_buff", "Stationary golden support aura expands without target contact."),
    (160000, 163000, "goku_super_saiyan", None, "ordinary_or_transition", "Ready movement before the next named rush."),
    (163000, 174000, "goku_super_saiyan", "Attacco di Meteoriti", "cinematic_melee", "Repeated body blows, changing vertical angles, spinning kick and final launch strike."),
    (174000, 179000, "goku_super_saiyan", None, "power_up", "Golden charging and Max-power transition."),
    (179000, 186000, "goku_super_saiyan", "Kamehameha Furiosa", "energy_beam", "Damaged close-up, rearward one-sided charge pose and broad blue eight-hit wave."),
    (186000, 198000, "goku_super_saiyan", None, "transformation", "Golden recovery and de-transformation to black hair."),
    (198000, 213000, "goku_base", None, "ordinary_or_transition", "Unlabelled state presentation and ordinary small yellow shots."),
    (213000, 223000, "goku_base", "Attacco di Meteoriti", "cinematic_melee", "Base-form rush versus Buu with alternating blows, aerial displacement and final burst."),
    (223000, 227000, "goku_base", None, "ordinary_or_transition", "Recovery and charge before the next beam."),
    (227000, 232000, "goku_base", "Kamehameha", "energy_beam", "Standard base-form two-hand blue wave and six-hit result."),
    (232000, 234000, "goku_base", None, "ordinary_or_transition", "Charge and positioning."),
    (234000, 237000, "goku_base", "Trasmissione Istantanea", "cinematic_melee", "Instant relocation into close contact followed by an orange impact."),
    (237000, 240000, "goku_base", None, "power_up", "Blue charge before a second instant movement."),
    (240000, 242000, "goku_base", "Trasmissione Istantanea", "cinematic_melee", "Separate instant relocation/rush occurrence."),
    (242000, 257000, "goku_base", "Super Sfera Genkidama", "cinematic_energy", "Enormous overhead blue sphere, target engulfment, Super Saiyan change and prolonged downward finishing push."),
    (257000, 258000, "goku_super_saiyan", None, "editor_cut", "Match cut before the next beam."),
    (258000, 262000, "goku_super_saiyan", "Super Kamehameha", "energy_beam", "Two-hand blue wave, whiteout and six-hit result."),
    (262000, 270000, "goku_super_saiyan", None, "power_up", "Golden charge and ready transitions."),
    (270000, 275000, "goku_super_saiyan", "Animo Saiyan", "pose_buff", "Stationary golden support aura with circular ground wave."),
    (275000, 281000, "goku_super_saiyan", "Kamehameha Istantanea", "cinematic_energy", "Close-up charge, instant relocation beside the target and immediate blue wave."),
    (281000, 282000, "goku_super_saiyan", None, "ordinary_or_transition", "Brief ready transition."),
    (282000, 292000, "goku_super_saiyan", "S. Raffica Onda Energ.", "energy_volley", "Two bright yellow hand spheres release repeated projectiles for a nine-hit result."),
    (292000, 301000, "goku_super_saiyan", "Trasmissione Istantanea", "cinematic_melee", "Instant relocation beside/behind the target, close combo and charged recovery."),
    (301000, 304000, "goku_super_saiyan", "Massima Potenza", "pose_buff", "Large orange spherical aura expands and collapses without target contact."),
    (304000, 307000, "goku_super_saiyan", "Kamehameha", "energy_beam", "Short standard blue wave occurrence."),
    (307000, 310000, "goku_super_saiyan", None, "power_up", "Golden charge before the rush."),
    (310000, 321000, "goku_super_saiyan", "Schianto della Meteorite", "cinematic_melee", "Cinematic rush, aerial chase, repeated blows, high displacement and final strike."),
    (321000, 327000, "goku_super_saiyan", None, "power_up", "Golden recovery and charge."),
    (327000, 334000, "goku_super_saiyan", "Super Kamehameha", "energy_beam", "Large blue beam, whiteout and eight-hit result."),
    (334000, 338000, "goku_super_saiyan_3", None, "ordinary_or_transition", "Ready and charge before instant movement."),
    (338000, 339000, "goku_super_saiyan_3", "Trasmissione Istantanea", "cinematic_melee", "Instant relocation directly beside/behind Buu."),
    (339000, 344000, "goku_super_saiyan_3", "Super Kamehameha", "energy_beam", "Long-haired charge pose and large blue six-hit wave."),
    (344000, 345000, "goku_super_saiyan_3", None, "ordinary_or_transition", "Brief charge transition."),
    (345000, 349000, "goku_super_saiyan_3", "Onda Super Esplosiva", "energy_radial", "Golden full-body radial burst expands from the attacker."),
    (349000, 353000, "goku_super_saiyan_3", None, "power_up", "Golden charge before instant movement."),
    (353000, 356000, "goku_super_saiyan_3", "Trasmissione Istantanea", "cinematic_melee", "Instant relocation beside/behind Buu and ready recovery."),
    (356000, 359000, "goku_super_saiyan_3", None, "power_up", "Golden charge before the finisher."),
    (359000, 375000, "goku_super_saiyan_3", "Artiglio del Drago", "cinematic_melee_energy", "Multiple golden body impacts followed by an orange dragon coiling around a sphere and crossing the victim."),
    (375000, 392000, "goku_super_saiyan_3", None, "transformation", "Extended recovery and de-transformation to black hair."),
    (392000, 414000, "goku", None, "transformation", "Staged transformations back through Super Saiyan forms."),
    (414000, 418000, "goku_super_saiyan_3", None, "ordinary_energy", "Unlabelled small yellow shots."),
    (418000, 422000, "vegito_base", "Raggio Diffuso", "energy_beam", "Raised arm releases a wide yellow-white horizontal five-hit beam."),
    (422000, 423000, "vegito_base", None, "ordinary_or_transition", "Brief charge transition."),
    (423000, 427000, "vegito_base", "Onda Esplosiva", "energy_radial", "White-gold radial burst centered on Vegito pushes the target away."),
    (427000, 428000, "vegito_base", None, "power_up", "Brief charge before the palm blast."),
    (428000, 431000, "vegito_base", "Attacco Big Bang", "energy_projectile", "Open palm forms a blue-white sphere and fires a large four-hit blast."),
    (431000, 437000, "vegito_base", None, "power_up", "Blue charge grows toward Max power."),
    (437000, 444000, "vegito_base", "Super Kamehameha", "energy_beam", "Blue sphere drawn across the body, large wave and eight-hit result."),
    (444000, 447000, "vegito_base", None, "power_up", "Blue charge before the next radial attack."),
    (447000, 450000, "vegito_base", "Onda Esplosiva", "energy_radial", "A second centered white-gold radial burst begins; recovery continues after this interval."),
]


def slug(text):
    return "".join(character if character.isalnum() else "_" for character in text.lower()).strip("_")


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    existing = {entry["id"] for entry in catalog["entries"]}
    added = []
    for start, end, actor, label, kind, beats in ROWS:
        suffix = slug(label) if label else kind
        identifier = f"{PREFIX}{actor}_{suffix}_{start // 1000}"
        if identifier in existing:
            continue
        added.append({
            "sourceStartMs": start,
            "sourceEndMs": end,
            "character": actor,
            "form": actor,
            "displayName": label,
            "kind": kind,
            "beats": beats,
            "evidenceState": EVIDENCE,
            "source": SOURCE,
            "id": identifier,
            "status": "observed_not_implemented",
        })
        existing.add(identifier)

    for entry in catalog["entries"]:
        start = entry.get("sourceStartMs")
        if entry.get("kind") == "section_checkpoint" and start is not None and start < 450000:
            entry["supersededBy"] = SOURCE

    catalog["entries"].extend(added)
    catalog["entries"].sort(key=lambda entry: (entry.get("sourceStartMs") is None,
                                                entry.get("sourceStartMs") or 0, entry["id"]))
    catalog["date"] = "2026-10-08"
    catalog["counts"]["entries"] = len(catalog["entries"])
    catalog["counts"]["earlyDetailedRows"] = len(ROWS)
    catalog["coverage"]["notes"][0] = (
        "0-449 s has full one-Hz sheet inspection and occurrence rows; exact source-PTS boundaries remain pending."
    )
    labels = {label.casefold() for label in catalog.get("distinctAttackLabels", [])}
    for _, _, _, label, _, _ in ROWS:
        if label and label.casefold() not in labels:
            catalog["distinctAttackLabels"].append(label.casefold())
            labels.add(label.casefold())
    catalog["distinctAttackLabels"].sort()
    catalog["counts"]["distinctAttackLabels"] = len(catalog["distinctAttackLabels"])
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"added {len(added)} early rows; catalog now has {len(catalog['entries'])} entries")


if __name__ == "__main__":
    main()
