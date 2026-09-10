# Addon networking

Register addon packets from the addon mod constructor through
`net.bullettrain.xenopixelsmod.api.network.AddonNetwork`.

Each registration supplies:

- a unique namespaced `ResourceLocation` packet id;
- one message class;
- `SERVERBOUND` or `CLIENTBOUND` direction;
- a `FriendlyByteBuf` encoder and decoder;
- a handler receiving an XenoPixels-owned `AddonPacketContext`.

Handlers use `AddonPacketContext.enqueueWork` for game-state changes. `sender()` is present only
for serverbound messages. Send with the direction-specific `sendToServer`, `sendToPlayer`,
`sendToAll`, `sendToTracking`, or `sendToTrackingAndSelf` helper.

Registrations close during NeoForge payload registration. XenoPixels sorts packet ids, assigns the
underlying numeric ids, and hashes packet ids, directions, and class names into protocol generation
1. Clients and servers with different addon packet registries therefore reject the connection.

The public API does not expose DragonMineZ networking classes. Its implementation currently uses
the DMZ compatibility shim, so any replacement of `libs/dragonminez-2.1.3.jar` requires source or
bytecode verification of that shim plus a full connection test.
