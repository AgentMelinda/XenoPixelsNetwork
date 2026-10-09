"""Author the pinned DMZ Saga skin-to-rig aliases, verifying overrides against its exact jar."""
from pathlib import Path
import hashlib
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "libs/dragonminez-2.1.3.jar"
EXPECTED = "5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581"
assert hashlib.sha256(JAR.read_bytes()).hexdigest() == EXPECTED
REFERENCE = ROOT / "tools/generated/dmz_decompiled_full/com/dragonminez/common/init"
classes = {}
for source in (REFERENCE / "entities/sagas").glob("*.java"):
    text = source.read_text(encoding="utf-8")
    for match in re.finditer(r"public (?:static )?class (\w+) extends (\w+)\s*\{", text):
        start, depth, end = match.end(), 1, match.end()
        while depth:
            depth += (text[end] == "{") - (text[end] == "}")
            end += 1
        body = text[start:end - 1]
        model = re.search(r'public String getGeckolibModelName\(\)\s*\{\s*return "([^"]+)";', body)
        qualified = source.stem if match.group(1) == source.stem else source.stem + "$" + match.group(1)
        classes[match.group(1)] = (match.group(2), model.group(1) if model else None, qualified)

def rig(cls):
    info = classes.get(cls)
    if info is None:
        return None
    parent, model, qualified = info
    return (model, qualified) if model else rig(parent)

registrations = (REFERENCE / "MainEntities.java").read_text(encoding="utf-8")
aliases, declaring = {}, {}
with zipfile.ZipFile(JAR) as jar:
    assets = set(jar.namelist())
    for match in re.finditer(r'ENTITY_TYPES\.register\(\s*"([^"]+)",\s*\(\) -> Builder\.of\(([\w.]+)::new', registrations):
        entity, constructor = match.groups()
        resolved = rig(constructor.split(".")[-1])
        if resolved is None:
            continue
        model, cls = resolved
        texture = f"assets/dragonminez/textures/entity/sagas/{entity}.png"
        geometry = f"assets/dragonminez/geo/entity/sagas/{model}.geo.json"
        if entity != model and texture in assets and geometry in assets:
            aliases[entity] = model
            declaring[cls] = model
    # The Saibaman renderer chooses a shared rig independently of entity overrides.
    for variant in range(1, 7):
        aliases[f"saga_saibaman{variant}"] = "saga_saibaman"

# A single javap invocation verifies every constant against the pinned dependency bytecode.
command = ["javap", "-classpath", str(JAR), "-c"]
command += ["com.dragonminez.common.init.entities.sagas." + cls for cls in sorted(declaring)]
bytecode = subprocess.run(command, check=True, capture_output=True, text=True).stdout
for cls, model in declaring.items():
    marker = "com.dragonminez.common.init.entities.sagas." + cls
    section = bytecode.split("public class " + marker, 1)[1].split("Compiled from", 1)[0]
    method = section.split("public java.lang.String getGeckolibModelName();", 1)[1].split("\n\n", 1)[0]
    assert "// String " + model in method, (cls, model)
saibaman = subprocess.run(["javap", "-classpath", str(JAR), "-c",
    "com.dragonminez.client.init.entities.model.sagas.DBSaibamanModel"],
    check=True, capture_output=True, text=True).stdout
assert "// String geo/entity/sagas/saga_saibaman.geo.json" in saibaman
assert "// String animations/entity/sagas/saga_saibaman.animation.json" in saibaman

target = ROOT / "src/main/java/net/bullettrain/xenopixelsmod/client/npc/DmzSagaModelAssets.java"
entries = ",\n".join(f'            Map.entry("{skin}", "{model}")' for skin, model in sorted(aliases.items()))
target.write_text('''package net.bullettrain.xenopixelsmod.client.npc;

import java.util.Map;

/** Pinned DragonMineZ 2.1.3 Saga texture aliases; verified by tools/verify_dmz_saga_model_assets.py. */
final class DmzSagaModelAssets {
    private DmzSagaModelAssets() {}
    private static final Map<String, String> RIGS = Map.ofEntries(
''' + entries + ''');

    static String rig(String skin) {
        String model = RIGS.get(skin);
        if (model != null) return model;
        // DMZ's numbered texture variants use their registered entity's original rig.
        String base = skin.replaceFirst("_[0-9]+$", "");
        return RIGS.get(base);
    }
}
''', encoding="utf-8")
print(f"Verified {len(declaring)} bytecode overrides; authored {len(aliases)} Saga skin aliases.")
