# Native CustomNPCs GUI implementation plan

Research date: 2026-10-09. This is a plan, not implemented GUI functionality.

The current UI document screen has local drawing/actions but no server-owned script GUI
sessions or input callbacks. Reusing operator editor save packets would apply the wrong
authority model. The safe integration point is the existing namespaced AddonNetwork,
preserving the main sequential ModNetwork registry.

## First operational slice

Implement an owner-bound server GUI/component model and a per-player session, full snapshot,
input/close/resize packets and a client UnblurredScreen with real Button/EditBox widgets.
Start with buttons, labels, rectangles, textures and text fields; implement factory,
show/get/update/close and their real callbacks together. Other widget methods must explicitly
refuse until their behavior exists. Carry no script source or callback object in packets.

Each input must match the authenticated sender's current session UUID/revision and component
identity/type. Validate visibility/enabled state and bounded input on the server; never accept
client item stacks or arbitrary commands. Recheck session identity after every callback,
because scripts may close or replace their GUI. Cleanup on disconnect/death/source reload
must fire close exactly once and discard old callbacks. Capture exact invoking script context
instead of broadcasting events to every NPC tab.

Suggested native policy, not official API limits: at most 256 components, depth 8, bounded
canvas dimensions 4096, text 4096 characters, snapshot 128 KiB and 16 inputs/player/tick.
Reject nonfinite numeric values, repeated component IDs, cycles, stale revisions and foreign
GUI/component handles. Texture IDs refer to resources, never filesystem paths.

Screen dimensions must come from bounded client acknowledgements; before an acknowledgement,
report unknown rather than inventing dimensions. Pause-game follows vanilla integrated-world
behavior; Escape honors the configured flag, and server close always takes precedence.

## Remaining widgets

Add real scroll/list/slider/text-area behavior, loaded-resource selection and actual entity
rendering. The existing UI portrait placeholder does not implement IEntityDisplay.

Inventory slots, carried stack and player inventory require a registered AbstractContainerMenu
and matching container screen with vanilla state/click synchronization. Follow the real native
NPC inventory menu. Do not model these as editable client pictures. Raw getMCSlot remains
subject to the existing scripting sandbox.

## Evidence required

Tests: packet length/truncation bounds, wrong owner, stale revision, disabled/unknown component,
callback replacement/reentrancy, subtree identity/cycles, numeric bounds, close/logout cleanup
and real Nashorn text/button callbacks. Fresh client and dedicated-side startup are required,
then actual input/resize/pause/item-conservation observations before runtime claims.

Source reviewed: NativeNpcApi, XenoPlayerAdapter, AddonNetwork, UiDocumentScreen/UiActions,
XenoNpcInventoryMenu, native editor widgets and the 16 local GUI interface contracts.
Official contract: [ICustomGui](https://www.kodevelopment.nl/customnpcs/api/1.18.2/noppes/npcs/api/gui/ICustomGui.html).
