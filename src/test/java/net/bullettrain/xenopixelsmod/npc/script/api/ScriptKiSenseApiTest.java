package net.bullettrain.xenopixelsmod.npc.script.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** 2026-09-28 owner request: ki sense controllable from scripts, like the editor's skill page. */
class ScriptKiSenseApiTest {
    @Test
    void theFacadeExposesKiSenseCalls() {
        Class<NativeXenoScriptApi> api = NativeXenoScriptApi.class;
        assertDoesNotThrow(() -> api.getMethod("setKiSense", ScriptNpc.class, boolean.class));
        assertDoesNotThrow(() -> api.getMethod("isKiSenseOn", ScriptNpc.class));
        assertDoesNotThrow(() -> api.getMethod("setKiSenseLockOn", ScriptNpc.class, boolean.class));
        assertDoesNotThrow(() -> api.getMethod("isKiSenseLockOn", ScriptNpc.class));
        assertDoesNotThrow(() -> api.getMethod("isKiSenseLockedOn", ScriptNpc.class, ScriptEntity.class));
    }

    @Test
    void theFacadeExposesEverySettingByKeyAndTheNativeIdentity() {
        Class<NativeXenoScriptApi> api = NativeXenoScriptApi.class;
        assertDoesNotThrow(() -> api.getMethod("listDmz", ScriptNpc.class));
        assertDoesNotThrow(() -> api.getMethod("getDmz", ScriptNpc.class, String.class));
        assertDoesNotThrow(() -> api.getMethod("setDmz", ScriptNpc.class, String.class, Object.class));
        assertDoesNotThrow(() -> api.getMethod("toggleDmz", ScriptNpc.class, String.class));
        assertDoesNotThrow(() -> api.getMethod("setNpcSetting", ScriptNpc.class, String.class, Object.class));
        assertDoesNotThrow(() -> api.getMethod("setRole", ScriptNpc.class, String.class));
        assertDoesNotThrow(() -> api.getMethod("setHome", ScriptNpc.class, double.class, double.class, double.class));
        assertDoesNotThrow(() -> api.getMethod("setRespawn", ScriptNpc.class, boolean.class));
    }
}
