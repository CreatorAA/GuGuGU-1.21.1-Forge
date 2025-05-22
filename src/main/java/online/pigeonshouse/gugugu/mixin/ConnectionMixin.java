package online.pigeonshouse.gugugu.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements RIFakeServerPlayer.FakeChannel {
    @Override
    @Accessor
    public abstract void setChannel(Channel channel);
}
