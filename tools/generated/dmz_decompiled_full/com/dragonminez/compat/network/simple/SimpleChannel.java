package com.dragonminez.compat.network.simple;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(
   bus = Bus.MOD
)
public final class SimpleChannel {
   private static final List<SimpleChannel> CHANNELS = new ArrayList<>();
   private final ResourceLocation name;
   private final String protocolVersion;
   private final Predicate<String> clientAcceptedVersions;
   private final Predicate<String> serverAcceptedVersions;
   private final List<SimpleChannel.PendingRegistration<?>> pending = new ArrayList<>();
   private final Map<Class<?>, SimpleChannel.PayloadBinding<?>> bindings = new ConcurrentHashMap<>();
   private boolean registered;

   public SimpleChannel(
      ResourceLocation name, Supplier<String> protocolVersion, Predicate<String> clientAcceptedVersions, Predicate<String> serverAcceptedVersions
   ) {
      this.name = name;
      this.protocolVersion = protocolVersion.get();
      this.clientAcceptedVersions = clientAcceptedVersions;
      this.serverAcceptedVersions = serverAcceptedVersions;
      if (this.protocolVersion == null || this.protocolVersion.isBlank()) {
         throw new IllegalArgumentException("Network protocol version for '" + name + "' must not be blank");
      } else if (this.acceptsClientVersion(this.protocolVersion) && this.acceptsServerVersion(this.protocolVersion)) {
         CHANNELS.add(this);
      } else {
         throw new IllegalArgumentException("Network protocol predicates for '" + name + "' reject its local version '" + this.protocolVersion + "'");
      }
   }

   public String protocolVersion() {
      return this.protocolVersion;
   }

   public boolean acceptsClientVersion(String version) {
      return this.clientAcceptedVersions.test(version);
   }

   public boolean acceptsServerVersion(String version) {
      return this.serverAcceptedVersions.test(version);
   }

   public <MSG> SimpleChannel.MessageBuilder<MSG> messageBuilder(Class<MSG> type, int id, NetworkDirection direction) {
      return new SimpleChannel.MessageBuilder<>(type, id, direction);
   }

   public <MSG> void sendToServer(MSG message) {
      SimpleChannel.PayloadBinding<MSG> binding = (SimpleChannel.PayloadBinding<MSG>)this.bindings.get(message.getClass());
      if (binding == null) {
         throw new IllegalStateException("No payload binding for " + message.getClass().getName());
      } else {
         PacketDistributor.sendToServer(binding.wrap(message), new CustomPacketPayload[0]);
      }
   }

   public <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
      SimpleChannel.PayloadBinding<MSG> binding = (SimpleChannel.PayloadBinding<MSG>)this.bindings.get(message.getClass());
      if (binding == null) {
         throw new IllegalStateException("No payload binding for " + message.getClass().getName());
      } else {
         PacketDistributor.sendToPlayer(player, binding.wrap(message), new CustomPacketPayload[0]);
      }
   }

   public <MSG> void sendToAllPlayers(MSG message) {
      SimpleChannel.PayloadBinding<MSG> binding = (SimpleChannel.PayloadBinding<MSG>)this.bindings.get(message.getClass());
      if (binding == null) {
         throw new IllegalStateException("No payload binding for " + message.getClass().getName());
      } else {
         PacketDistributor.sendToAllPlayers(binding.wrap(message), new CustomPacketPayload[0]);
      }
   }

   public <MSG> void sendToTrackingEntityAndSelf(MSG message, Entity entity) {
      SimpleChannel.PayloadBinding<MSG> binding = (SimpleChannel.PayloadBinding<MSG>)this.bindings.get(message.getClass());
      if (binding == null) {
         throw new IllegalStateException("No payload binding for " + message.getClass().getName());
      } else {
         PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, binding.wrap(message), new CustomPacketPayload[0]);
      }
   }

   public <MSG> void sendToTrackingEntity(MSG message, Entity entity) {
      SimpleChannel.PayloadBinding<MSG> binding = (SimpleChannel.PayloadBinding<MSG>)this.bindings.get(message.getClass());
      if (binding == null) {
         throw new IllegalStateException("No payload binding for " + message.getClass().getName());
      } else {
         PacketDistributor.sendToPlayersTrackingEntity(entity, binding.wrap(message), new CustomPacketPayload[0]);
      }
   }

   @SubscribeEvent
   public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
      for (SimpleChannel channel : List.copyOf(CHANNELS)) {
         channel.flush(event.registrar(channel.protocolVersion));
      }
   }

   private void flush(PayloadRegistrar registrar) {
      if (!this.registered) {
         this.registered = true;

         for (SimpleChannel.PendingRegistration<?> reg : this.pending) {
            reg.register(registrar, this.name, this.bindings);
         }
      }
   }

   public final class MessageBuilder<MSG> {
      private final Class<MSG> type;
      private final int id;
      private final NetworkDirection direction;
      private BiConsumer<MSG, FriendlyByteBuf> encoder;
      private Function<FriendlyByteBuf, MSG> decoder;
      private BiConsumer<MSG, Supplier<NetworkEvent.Context>> consumer;

      MessageBuilder(Class<MSG> type, int id, NetworkDirection direction) {
         this.type = type;
         this.id = id;
         this.direction = direction;
      }

      public SimpleChannel.MessageBuilder<MSG> encoder(BiConsumer<MSG, FriendlyByteBuf> encoder) {
         this.encoder = encoder;
         return this;
      }

      public SimpleChannel.MessageBuilder<MSG> decoder(Function<FriendlyByteBuf, MSG> decoder) {
         this.decoder = decoder;
         return this;
      }

      public SimpleChannel.MessageBuilder<MSG> consumerMainThread(BiConsumer<MSG, Supplier<NetworkEvent.Context>> consumer) {
         this.consumer = consumer;
         return this;
      }

      public void add() {
         SimpleChannel.this.pending.add(new SimpleChannel.PendingRegistration<>(this.type, this.id, this.direction, this.encoder, this.decoder, this.consumer));
      }
   }

   private static record PayloadBinding<MSG>(Type<SimpleChannel.WrappedPayload<MSG>> payloadType) {
      SimpleChannel.WrappedPayload<MSG> wrap(MSG message) {
         return new SimpleChannel.WrappedPayload<>(this.payloadType, message);
      }
   }

   private static record PendingRegistration<MSG>(
      Class<MSG> type,
      int id,
      NetworkDirection direction,
      BiConsumer<MSG, FriendlyByteBuf> encoder,
      Function<FriendlyByteBuf, MSG> decoder,
      BiConsumer<MSG, Supplier<NetworkEvent.Context>> consumer
   ) {
      void register(PayloadRegistrar registrar, ResourceLocation channelName, Map<Class<?>, SimpleChannel.PayloadBinding<?>> bindings) {
         ResourceLocation payloadId = ResourceLocation.fromNamespaceAndPath(
            channelName.getNamespace(), channelName.getPath() + "/" + this.id + "_" + this.type.getSimpleName().toLowerCase()
         );
         Type<SimpleChannel.WrappedPayload<MSG>> payloadType = new Type(payloadId);
         StreamCodec<RegistryFriendlyByteBuf, SimpleChannel.WrappedPayload<MSG>> codec = StreamCodec.of(
            (buf, wrapped) -> this.encoder.accept((MSG)wrapped.message(), buf), buf -> new SimpleChannel.WrappedPayload(payloadType, this.decoder.apply(buf))
         );
         IPayloadHandler<SimpleChannel.WrappedPayload<MSG>> handler = (payload, ctx) -> this.consumer.accept((MSG)payload.message(), NetworkEvent.wrap(ctx));
         if (this.direction == NetworkDirection.PLAY_TO_SERVER) {
            registrar.playToServer(payloadType, codec, handler);
         } else {
            registrar.playToClient(payloadType, codec, handler);
         }

         bindings.put(this.type, new SimpleChannel.PayloadBinding(payloadType));
      }
   }

   public static record WrappedPayload<MSG>(Type<SimpleChannel.WrappedPayload<MSG>> payloadType, MSG message) implements CustomPacketPayload {
      public Type<? extends CustomPacketPayload> type() {
         return this.payloadType;
      }
   }
}
