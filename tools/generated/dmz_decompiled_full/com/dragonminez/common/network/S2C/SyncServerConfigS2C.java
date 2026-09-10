package com.dragonminez.common.network.S2C;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.CompressionUtil;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class SyncServerConfigS2C {
   private final String configPath;
   private final byte[] payload;
   private final boolean reset;

   public SyncServerConfigS2C(String configPath, String jsonPayload, boolean reset) {
      this.configPath = configPath;
      this.payload = CompressionUtil.compress(jsonPayload);
      this.reset = reset;
   }

   public SyncServerConfigS2C(FriendlyByteBuf buf) {
      this.configPath = buf.readUtf();
      this.payload = buf.readByteArray();
      this.reset = buf.readBoolean();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeUtf(this.configPath);
      buf.writeByteArray(this.payload);
      buf.writeBoolean(this.reset);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
               if (this.reset) {
                  ConfigManager.beginServerSyncBatch();
               }

               String json = CompressionUtil.decompress(this.payload);
               ConfigManager.applySpecificSyncedConfig(this.configPath, json);
            }));
      ctx.get().setPacketHandled(true);
   }
}
