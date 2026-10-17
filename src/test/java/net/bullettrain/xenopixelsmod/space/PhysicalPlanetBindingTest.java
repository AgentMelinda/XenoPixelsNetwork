package net.bullettrain.xenopixelsmod.space;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PhysicalPlanetBindingTest {
    @Test void identityAndClearRoundTripInBoundedPayload() {
        for(UUID id:new UUID[]{UUID.randomUUID(),null}) {
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try {
                var packet=new PhysicalPlanetBinding(id);
                PhysicalPlanetBinding.encode(packet,buf);
                assertEquals(id==null?1:17,buf.readableBytes());
                assertEquals(packet,PhysicalPlanetBinding.decode(buf));
                assertEquals(0,buf.readableBytes());
            } finally {buf.release();}
        }
    }
    @Test void incompleteUuidFailsInsteadOfInventingBody() {
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {buf.writeBoolean(true);assertThrows(IndexOutOfBoundsException.class,()->PhysicalPlanetBinding.decode(buf));}
        finally {buf.release();}
    }
}
