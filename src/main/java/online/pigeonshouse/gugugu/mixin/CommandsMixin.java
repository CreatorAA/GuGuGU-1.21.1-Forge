package online.pigeonshouse.gugugu.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Commands.class)
public class CommandsMixin {
    @Inject(at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V"),
            method = "<init>")
    private void initCommand(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo info) {
        Commands commands = (Commands) (Object) this;
        CommandDispatcher<CommandSourceStack> dispatcher = commands.getDispatcher();
        MinecraftServerEvents.CommandRegisterEvent commandRegisterEvent =
                new MinecraftServerEvents.CommandRegisterEvent(dispatcher, selection, context);

        MinecraftServerEvents.COMMAND_REGISTER.dispatch(commandRegisterEvent);
    }
}
