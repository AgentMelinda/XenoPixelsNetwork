#!/usr/bin/env python3
"""Reproduce FML's mod-sort graph for a mods folder and name the cause of any cycle.

NeoForge aborts the whole instance before a single mod loads when the mod-ordering graph
contains a cycle, and the crash only prints the member mod ids:

    Detected a mod dependency cycle: dmzcustomforms, dragonminez, xenopixelsmod

That does not say which declaration is responsible, and the declarations live inside jars.
This script rebuilds the same graph offline and prints, for every cycle, the exact
`[[dependencies.X]]` entry and the jar it came from.

Edge rules are taken from net.neoforged.fml.loading.ModSorter#addDependency in
fancymodloader 4.0.x:

  * a dependency is owned by the `[[mods]]` entry whose id follows `[[dependencies.` -
    `ModInfo` reads `getConfigList("dependencies", this.modId)`, so a jar can never declare
    ordering on behalf of a mod it does not ship;
  * ordering AFTER  -> edge dependency -> owner;
  * ordering BEFORE -> edge owner -> dependency;
  * ordering NONE (the default) contributes no edge;
  * a dependency on an absent mod contributes no edge, whatever its type;
  * self-edges inside one jar are skipped.

`dependencyOverrides` in config/fml.toml cannot break a cycle: a "-dep" entry only drops a
version/incompatibility constraint, while "+dep" adds another AFTER edge. Breaking a cycle
means changing a mods.toml or removing one of the mods.

Usage:
    python scripts/diagnose_mod_cycles.py "<path to the instance mods folder>"
"""

from __future__ import annotations

import collections
import io
import os
import re
import sys
import zipfile

MODS_TOML = ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
HEADER = re.compile(r"(?m)^\s*(\[\[?[A-Za-z0-9_.$-]+\]?\])\s*$")
MOD_ID = re.compile(r'modId\s*=\s*"([^"]+)"')
ORDERING = re.compile(r'ordering\s*=\s*"([^"]+)"')
DEP_TYPE = re.compile(r'type\s*=\s*"([^"]+)"')


def parse_toml(text):
    """Return (provided modIds, [(owner, dep, ordering, type)]) from one mods.toml."""
    parts = HEADER.split(text)
    provides, deps = [], []
    for i in range(1, len(parts) - 1, 2):
        header, body = parts[i], parts[i + 1]
        found = MOD_ID.search(body)
        if not found:
            continue
        if header == "[[mods]]":
            provides.append(found.group(1))
        elif header.startswith("[[dependencies."):
            owner = header[len("[[dependencies.") : -2]
            ordering = ORDERING.search(body)
            dep_type = DEP_TYPE.search(body)
            deps.append(
                (
                    owner,
                    found.group(1),
                    ordering.group(1).upper() if ordering else "NONE",
                    dep_type.group(1) if dep_type else "optional",
                )
            )
    return provides, deps


def scan_jar(raw, label, found, depth=0):
    try:
        archive = zipfile.ZipFile(io.BytesIO(raw))
    except Exception as error:  # not a jar, or truncated
        print(f"  ! could not read {label}: {error}", file=sys.stderr)
        return
    names = archive.namelist()
    for candidate in MODS_TOML:
        if candidate in names:
            provides, deps = parse_toml(archive.read(candidate).decode("utf-8", "replace"))
            if provides:
                found.append((label, provides, deps))
            break
    if depth < 2:  # jar-in-jar: a modId often ships nested rather than as its own file
        for name in names:
            if name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                scan_jar(archive.read(name), f"{label} :: {os.path.basename(name)}", found, depth + 1)


def strongly_connected(edges, nodes):
    """Tarjan's SCC, iterative so a 200-mod graph cannot blow the Python stack."""
    index, low, on_stack, stack, order, result = {}, {}, set(), [], [0], []
    for root in nodes:
        if root in index:
            continue
        work = [(root, iter(edges.get(root, ())))]
        index[root] = low[root] = order[0]
        order[0] += 1
        stack.append(root)
        on_stack.add(root)
        while work:
            node, children = work[-1]
            advanced = False
            for child in children:
                if child not in index:
                    index[child] = low[child] = order[0]
                    order[0] += 1
                    stack.append(child)
                    on_stack.add(child)
                    work.append((child, iter(edges.get(child, ()))))
                    advanced = True
                    break
                if child in on_stack:
                    low[node] = min(low[node], index[child])
            if advanced:
                continue
            work.pop()
            if work:
                low[work[-1][0]] = min(low[work[-1][0]], low[node])
            if low[node] == index[node]:
                component = []
                while True:
                    popped = stack.pop()
                    on_stack.discard(popped)
                    component.append(popped)
                    if popped == node:
                        break
                if len(component) > 1:
                    result.append(sorted(component))
    return result


def main(folder):
    found = []
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".jar"):
            continue
        with open(os.path.join(folder, name), "rb") as handle:
            scan_jar(handle.read(), name, found, 0)

    providers = collections.defaultdict(list)
    for label, provides, _ in found:
        for mod_id in provides:
            providers[mod_id].append(label)
    present = set(providers) | {"minecraft", "neoforge"}
    print(f"Scanned {folder}")
    print(f"  {len(found)} mod entries, {len(present)} distinct mod ids\n")

    duplicates = {k: v for k, v in providers.items() if len(v) > 1}
    if duplicates:
        print("Mod ids provided by more than one file (FML keeps one and may pick either):")
        for mod_id, files in sorted(duplicates.items()):
            print(f"  {mod_id}")
            for f in files:
                print(f"      {f}")
        print()

    edges = collections.defaultdict(set)
    why = collections.defaultdict(list)
    for label, provides, deps in found:
        for owner, dep, ordering, dep_type in deps:
            if owner not in provides or dep not in present or owner == dep:
                continue
            if ordering == "AFTER":
                a, b = dep, owner
            elif ordering == "BEFORE":
                a, b = owner, dep
            else:
                continue
            edges[a].add(b)
            why[(a, b)].append(f"{label}: [[dependencies.{owner}]] {ordering} {dep} ({dep_type})")

    cycles = strongly_connected(edges, sorted(present))
    if not cycles:
        print("No ordering cycle. This mod set sorts cleanly.")
        return 0

    print(f"{len(cycles)} ordering cycle(s) - FML will refuse to boot:\n")
    for cycle in cycles:
        print("  cycle: " + ", ".join(cycle))
        for a in cycle:
            for b in sorted(edges.get(a, ())):
                if b in cycle:
                    for reason in why[(a, b)]:
                        print(f"      {a} -> {b}   {reason}")
        print("    break it by removing one of those mods, or by editing the mods.toml that")
        print("    declares one of those edges. config/fml.toml dependencyOverrides cannot.")
        print()
    return 1


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
