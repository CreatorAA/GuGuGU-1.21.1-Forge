package online.pigeonshouse.gugugu.chat.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.chat.MessagePipeline;
import online.pigeonshouse.gugugu.chat.MessageProcessorInfo;

public class ChatCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, MessagePipeline pipeline) {
        dispatcher.register(
                Commands.literal("chatEvent")
                        .then(Commands.literal("components")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        Component componentsInfo = buildComponentsInfo(pipeline, player);
                                        source.sendSystemMessage(componentsInfo);
                                    } else {
                                        source.sendSystemMessage(
                                                Component.literal("This command can only be executed by a player")
                                                        .withStyle(ChatFormatting.RED));
                                    }
                                    return 1;
                                })
                        )
        );
    }

    private static Component buildComponentsInfo(MessagePipeline pipeline, ServerPlayer sender) {
        MutableComponent header = Component.literal("=== 消息处理组件列表 ===\n")
                .withStyle(ChatFormatting.GOLD);

        MutableComponent content = Component.empty();

        for (MessageProcessorInfo info : pipeline.getProcessorInfos()) {
            MutableComponent infoComponent = Component.literal("\n● ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(info.getName()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(info.getDescription().copy())
                    .append(Component.literal("\n  状态: ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(buildStatusComponent(info));

            if (!info.getExamples().isEmpty()) {
                infoComponent.append(Component.literal("\n  示例演示（无视禁用）:").withStyle(ChatFormatting.DARK_AQUA));

                for (MessageProcessorInfo.Example example : info.getExamples()) {
                    Component processed = pipeline.test(sender, info.getName(), example.getExample());

                    infoComponent.append(
                            Component.literal("\n    ▶ 输入: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(example.getExample()).withStyle(ChatFormatting.WHITE))
                    );

                    if (!example.getDescription().getString().isEmpty()) {
                        infoComponent.append(
                                Component.literal("\n      描述: ").withStyle(ChatFormatting.AQUA)
                                        .append(example.getDescription())
                        );
                    }

                    infoComponent.append(
                            Component.literal("\n      效果: ").withStyle(ChatFormatting.DARK_GRAY)
                                    .append(processed)
                    );
                }

            }

            content = content.append(infoComponent).append(Component.literal("\n"));
        }

        return header.append(content);
    }

    private static Component buildStatusComponent(MessageProcessorInfo info) {
        return Component.literal(info.isEnabled() ? "✔ 启用" : "✖ 禁用")
                .withStyle(info.isEnabled() ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED);
    }
}