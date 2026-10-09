// Native XenoPixels NPC script. Paste into one enabled ECMAScript tab.
// Save Lord Slug (Giant) in native Clone Tab 1 before enabling replacement.
var CONFIG = {
    transformOnDeath: true,
    cloneTab: 1,
    cloneName: "Lord Slug (Giant)",
    preventGiantReplacementLoop: true,
    trackEntityMaxHealth: true,
    fixedMaxHealth: 40,
    passiveRegenPerSecond: 6000.0,
    combatRegenPerSecond: 0.0,
    combatLingerSeconds: 6,
    healToFullOnSpawn: false,
    debug: false
};

var _lastCombatTick = -100000;
var _lastTickTime = -1;
var _lastHp = -1;
var _healBuffer = 0;
var _ready = false;
var _deathHandled = false;

function _now(npc) { return npc.getWorld().getTotalTime(); }
function _alive(npc) { return !npc.isKilled() && npc.getHealth() > 0; }
function _say(message) { if (CONFIG.debug) log.line("[regen] " + message); }

function _maxHp(npc) {
    if (!CONFIG.trackEntityMaxHealth && npc.getMaxHealth() != CONFIG.fixedMaxHealth) {
        npc.setMaxHealth(CONFIG.fixedMaxHealth);
    }
    return npc.getMaxHealth();
}

function _inCombat(npc, now) {
    var target = npc.getAttackTarget();
    if (target != null && target.isAlive()) {
        _lastCombatTick = now;
        return true;
    }
    return now - _lastCombatTick < CONFIG.combatLingerSeconds * 20;
}

function init(event) {
    var npc = event.npc;
    _lastCombatTick = -100000;
    _lastTickTime = _now(npc);
    _lastHp = npc.getHealth();
    _healBuffer = 0;
    _ready = true;
    _deathHandled = false;
    if (CONFIG.healToFullOnSpawn && _alive(npc)) npc.setHealth(_maxHp(npc));
}

function tick(event) {
    var npc = event.npc;
    if (!_alive(npc)) return;
    if (!_ready) init(event);
    var now = _now(npc);
    var elapsed = now - _lastTickTime;
    _lastTickTime = now;
    if (elapsed <= 0) return;
    var dt = Math.min(elapsed, 100) / 20.0;
    var hp = npc.getHealth();
    var max = _maxHp(npc);
    if (_lastHp >= 0 && hp < _lastHp - 0.01) _lastCombatTick = now;
    if (hp >= max) {
        _healBuffer = 0;
        _lastHp = hp;
        return;
    }
    var fighting = _inCombat(npc, now);
    var rate = fighting ? CONFIG.combatRegenPerSecond : CONFIG.passiveRegenPerSecond;
    if (rate > 0) {
        _healBuffer += rate * dt;
        var whole = Math.floor(_healBuffer);
        if (whole >= 1) {
            _healBuffer -= whole;
            hp = Math.min(max, hp + whole);
            npc.setHealth(hp);
            _say((fighting ? "combat" : "passive") + " +" + whole + " -> " + hp + "/" + max);
        }
    }
    _lastHp = npc.getHealth();
}

function damaged(event) { _lastCombatTick = _now(event.npc); }
function meleeAttack(event) { _lastCombatTick = _now(event.npc); }
function rangedLaunched(event) { _lastCombatTick = _now(event.npc); }

function died(event) {
    if (_deathHandled) return;
    _deathHandled = true;
    _ready = false;
    _lastCombatTick = -100000;
    _healBuffer = 0;
    _lastHp = -1;
    if (!CONFIG.transformOnDeath) return;
    var npc = event.npc;
    if (CONFIG.preventGiantReplacementLoop && String(npc.getName()) == CONFIG.cloneName) return;
    var clone = npc.getWorld().spawnClone(npc.getX(), npc.getY(), npc.getZ(), CONFIG.cloneTab, CONFIG.cloneName);
    if (clone == null) {
        log.line("[Lord Slug] Clone not found in tab " + CONFIG.cloneTab + ": " + CONFIG.cloneName);
        return;
    }
    npc.despawn();
}
