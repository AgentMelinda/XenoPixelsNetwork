// Forge (world event) script tab. Scripter tool: right-click the air > Forge Scripts > "+".
//
// Hooks: init, serverTick, playerLogin, playerLogout, playerRespawn, playerChangedDimension,
// livingDeath, livingHurt, entityJoin, blockBreak, blockPlace, serverChat, rightClickBlock,
// rightClickItem, leftClickBlock, entityInteract, explosion.
// event: player, entity, source, world, x/y/z, block, item, message, damage (writable),
// cancel() / isCancelable(). Scope: world, XenoPixels, XenoAPI, log.

function init(event) {
    log("Forge tab loaded");
}

function blockBreak(event) {
    // Diamond ore is protected at spawn.
    if (event.block == "minecraft:diamond_ore" && Math.abs(event.x) < 64 && Math.abs(event.z) < 64) {
        event.player.message("Spawn diamonds are protected.");
        event.cancel();
    }
}

function livingHurt(event) {
    // Fall damage is halved for everyone.
    if (event.message == "fall") event.setDamage(event.damage / 2);
}

function livingDeath(event) {
    if (event.source && event.source.isPlayer()) {
        world.broadcast(event.source.getName() + " defeated " + event.entity.getName());
    }
}

function playerLogin(event) {
    event.player.message("Welcome to the world, " + event.player.getName() + "!");
}

function explosion(event) {
    // No explosions within 32 blocks of 0,0.
    if (event.x * event.x + event.z * event.z < 32 * 32) event.cancel();
}
