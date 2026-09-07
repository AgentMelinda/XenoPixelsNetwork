# Third-party notices

## DragonMineZ integration

The locally resolved `libs/dragonminez-2.1.3.jar` declares version 2.1.3 and
`license="GNU General Public License v3.0"` in `META-INF/neoforge.mods.toml`.
The clone combat policy and executor are original XenoPixels implementations using verified
public dependency APIs; no decompiled `SagasCombatBrain` or `CombatContext` source was ported.
Dependency inspection was used to establish signatures, resource spending and damage attribution.

This notice does not relicense XenoPixels, grant permission to copy DragonMineZ implementation,
or resolve GPL linking/distribution obligations for this All Rights Reserved project. Any
source-port or distribution decision requiring permission/licensing review remains pending.

## Create

The copycat model-data, appearance, and redraw implementation is adapted from the Create
mod's copycat implementation.

MIT License

Copyright (c) The Create Team / The Creators of Create

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## CC:LiftLink

`compat/create/elevator/ElevatorHelpers.java` and `compat/create/elevator/ElevatorMethods.java`
are derived from CC:LiftLink (https://github.com/tiktop101/CC-LiftLink), which targets
Minecraft 1.20.1 / Forge; ours is the 1.21.1 / NeoForge port of it. The Lua-facing method names
and returned table keys are kept identical so programs written against CC:LiftLink run
unchanged.

CC:LiftLink is licensed under the Mozilla Public License 2.0. Under MPL-2.0 section 3.3 those
two files remain covered by the MPL even though the rest of XenoPixels Network does not: each
carries an MPL header, and their source — including our modifications — is distributed with
this project. The remainder of XenoPixels Network is unaffected and keeps its own licence.

A copy of the MPL-2.0 is available at https://mozilla.org/MPL/2.0/.
