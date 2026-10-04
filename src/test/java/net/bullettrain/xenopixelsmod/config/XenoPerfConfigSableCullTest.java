package net.bullettrain.xenopixelsmod.config;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoPerfConfigSableCullTest {

    @Test
    void sableCullDefaultsOffAndOldPerfConfigsMigrateOff() {
        XenoPerfConfig.Data saved = XenoPerfConfig.snapshot();
        try {
            assertFalse(new XenoPerfConfig.Data().sableContraptionCullEnabled);

            XenoPerfConfig.Data v2 = new XenoPerfConfig.Data();
            v2.version = 2;
            v2.sableContraptionCullEnabled = true;
            XenoPerfConfig.apply(v2);
            assertFalse(XenoPerfConfig.sableContraptionCullEnabled);

            XenoPerfConfig.Data v3 = new XenoPerfConfig.Data();
            v3.version = 3;
            v3.sableContraptionCullEnabled = true;
            XenoPerfConfig.apply(v3);
            assertTrue(XenoPerfConfig.sableContraptionCullEnabled);
        } finally {
            XenoPerfConfig.apply(saved);
        }
    }

    @Test
    void sableCullClientMissingFieldIsOff() {
        XenoClientConfig.Data saved = XenoClientConfig.snapshot();
        try {
            XenoClientConfig.Data d = XenoClientConfig.snapshot();
            d.sableContraptionCullClient = null;
            XenoClientConfig.apply(d);
            assertFalse(XenoClientConfig.sableContraptionCullClient);

            d.sableContraptionCullClient = true;
            XenoClientConfig.apply(d);
            assertTrue(XenoClientConfig.sableContraptionCullClient);
        } finally {
            XenoClientConfig.apply(saved);
        }
    }
}
