package com.dragonminez.compat.network;

import com.dragonminez.compat.network.simple.SimpleChannel;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

public final class NetworkRegistry {
   private NetworkRegistry() {
   }

   public static NetworkRegistry.ChannelBuilder newSimpleChannel(
      ResourceLocation name, Supplier<String> networkProtocolVersion, Predicate<String> clientAcceptedVersions, Predicate<String> serverAcceptedVersions
   ) {
      return NetworkRegistry.ChannelBuilder.named(name)
         .networkProtocolVersion(networkProtocolVersion)
         .clientAcceptedVersions(clientAcceptedVersions)
         .serverAcceptedVersions(serverAcceptedVersions);
   }

   public static final class ChannelBuilder {
      private final ResourceLocation name;
      private Supplier<String> networkProtocolVersion;
      private Predicate<String> clientAcceptedVersions;
      private Predicate<String> serverAcceptedVersions;

      private ChannelBuilder(ResourceLocation name) {
         this.name = name;
      }

      public static NetworkRegistry.ChannelBuilder named(ResourceLocation name) {
         return new NetworkRegistry.ChannelBuilder(name);
      }

      public NetworkRegistry.ChannelBuilder networkProtocolVersion(Supplier<String> version) {
         this.networkProtocolVersion = Objects.requireNonNull(version, "networkProtocolVersion");
         return this;
      }

      public NetworkRegistry.ChannelBuilder clientAcceptedVersions(Predicate<String> accepted) {
         this.clientAcceptedVersions = Objects.requireNonNull(accepted, "clientAcceptedVersions");
         return this;
      }

      public NetworkRegistry.ChannelBuilder serverAcceptedVersions(Predicate<String> accepted) {
         this.serverAcceptedVersions = Objects.requireNonNull(accepted, "serverAcceptedVersions");
         return this;
      }

      public SimpleChannel simpleChannel() {
         if (this.networkProtocolVersion != null && this.clientAcceptedVersions != null && this.serverAcceptedVersions != null) {
            return new SimpleChannel(this.name, this.networkProtocolVersion, this.clientAcceptedVersions, this.serverAcceptedVersions);
         } else {
            throw new IllegalStateException("Network channel '" + this.name + "' is missing protocol compatibility configuration");
         }
      }
   }
}
