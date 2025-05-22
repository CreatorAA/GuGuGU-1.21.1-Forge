package online.pigeonshouse.gugugu.fakeplayer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;

@Slf4j
public class RIFakeServerPlayer extends ServerPlayer {
    public static EntityDataAccessor<Byte> DATA_PLAYER_MODE_CUSTOMISATION = ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION;

    public RIFakeServerPlayer(MinecraftServer server, ServerLevel worldIn, GameProfile profile, ClientInformation cli) {
        super(server, worldIn, profile, cli);
    }

    public static Connection createFakeConnection(PacketFlow packetFlow) {
        return new RIFakeConnection(packetFlow);
    }

    @Override
    public void tick() {
        try {
            super.tick();
            this.doTick();
        } catch (NullPointerException ignored) {
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        shakeOff();
        super.die(damageSource);
        respawn();
        disconnect(getCombatTracker().getDeathMessage());
    }

    @Override
    public void respawn() {
        ServerboundClientCommandPacket p = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
        connection.handleClientCommand(p);
    }

    public void disconnect(Component component) {
        shakeOff();

        this.server.tell(new TickTask(this.server.getTickCount(),
                () -> this.connection.onDisconnect(new DisconnectionDetails(component))));
    }

    private void shakeOff() {
        if (getVehicle() instanceof Player) stopRiding();

        for (Entity passenger : getIndirectPassengers()) {
            if (passenger instanceof Player) passenger.stopRiding();
        }
    }

    @Override
    public void unsetRemoved() {
        super.unsetRemoved();
    }

    @Override
    public boolean allowsListing() {
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        doCheckFallDamage(0.0, y, 0.0, onGround);
    }

    @Override
    public Entity changeDimension(DimensionTransition dimensionTransition) {
        super.changeDimension(dimensionTransition);
        if (wonGame) {
            ServerboundClientCommandPacket p = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
            connection.handleClientCommand(p);
        }

        if (connection.player.isChangingDimension()) {
            connection.player.hasChangedDimension();
        }
        return connection.player;
    }

    @Override
    public void onEquipItem(final EquipmentSlot slot, final ItemStack previous, final ItemStack stack) {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    public interface FakeChannel {
        void setChannel(Channel channel);
    }

    private static class RIFakeConnection extends Connection {
        public RIFakeConnection(PacketFlow packetFlow) {
            super(packetFlow);
            ((FakeChannel) this).setChannel(new RIFakeChannel());
        }

        @Override
        public void setReadOnly() {

        }

        @Override
        public void handleDisconnection() {

        }

        @Override
        public void setListenerForServerboundHandshake(PacketListener packetListener) {

        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener) {

        }
    }

    private static class RIFakeChannel extends EmbeddedChannel {
        public RIFakeChannel() {
            config().setAutoRead(true);
            config().setAllocator(ByteBufAllocator.DEFAULT);

            pipeline().addLast(new SimpleChannelInboundHandler<>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                    ReferenceCountUtil.release(msg);
                }
            });

            pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    promise.setSuccess();
                    ReferenceCountUtil.release(msg);
                }
            });
        }
    }
}
