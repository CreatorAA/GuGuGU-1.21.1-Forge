package online.pigeonshouse.gugugu.chat;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import online.pigeonshouse.gugugu.chat.processors.ComponentRenderer;
import online.pigeonshouse.gugugu.chat.processors.JourneyMapUtilProcessor;
import online.pigeonshouse.gugugu.chat.processors.MentionNotifier;
import online.pigeonshouse.gugugu.chat.processors.XaeroMapUtilProcessor;
import online.pigeonshouse.gugugu.chat.processors.parser.HandItemParser;
import online.pigeonshouse.gugugu.chat.processors.parser.LinkParser;
import online.pigeonshouse.gugugu.chat.processors.parser.MentionParser;
import online.pigeonshouse.gugugu.chat.processors.parser.TeleportRequestParser;
import online.pigeonshouse.gugugu.event.EventCallback;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;

@Slf4j
public class ChatEventHandler implements EventCallback<MinecraftServerEvents.PlayerChatEvent> {
    @Getter
    private final MessagePipeline pipeline;

    public ChatEventHandler() {
        this.pipeline = new MessagePipeline()
                .registerFinalProcessor(new ComponentRenderer(), buildComponentRendererInfo())
                .registerProcessor(new MentionParser(), buildMentionParserInfo())
                .registerProcessor(new LinkParser(), buildLinkParserInfo())
                .registerProcessor(new MentionNotifier(), buildMentionNotifierInfo())
                .registerProcessor(new TeleportRequestParser(), buildTeleportRequestProcessorInfo())
                .registerProcessor(new XaeroMapUtilProcessor(), buildXaeroMapUtilProcessorInfo())
                .registerProcessor(new JourneyMapUtilProcessor(), buildJourneyMapUtilProcessorInfo())
                .registerProcessor(new HandItemParser(), buildHandItemParserInfo());
    }

    private static MessageProcessorInfo buildMentionParserInfo() {
        Component description = Component.literal("允许使用").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("@{").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("玩家名称").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("}").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("或").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("@all").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("的方式来对特定的玩家进行提示").withStyle(ChatFormatting.GRAY));

        return MessageProcessorInfo.of("mention", description)
                .addExample(new MessageProcessorInfo.Example("@all 兄弟们我挖到钻石辣！！！",
                        Component.literal("此消息会为").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("\"@all\"").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("标为橙色显示"))))
                .addExample(new MessageProcessorInfo.Example("@Steven 你昨天是不是偷偷拿了@Alice 的巧克力",
                        Component.literal("此消息会为").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("\"@Steven\"").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("、"))
                                .append(Component.literal("\"@Alice\"").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("标为橙色显示"))))
                .addExample(new MessageProcessorInfo.Example("@all 这俩人有问题：@Alice @Steven",
                        Component.literal("此消息会为").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("\"@all\"").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("、"))
                                .append(Component.literal("\"@Alice\"").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("、"))
                                .append(Component.literal("\"@Steven\"").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("标为橙色显示"))));
    }

    private static MessageProcessorInfo buildLinkParserInfo() {
        Component description = Component.literal("当玩家消息中出现有效的")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Http链接").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("时，自动将其转换为点击访问的链接").withStyle(ChatFormatting.GRAY));

        return MessageProcessorInfo.of("link", description)
                .addExample(new MessageProcessorInfo.Example("https://www.baidu.com",
                        Component.literal("此消息会为").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("\"https://www.baidu.com\"").withStyle(ChatFormatting.BLUE))
                                .append(Component.literal("标为蓝色显示，并且点击后可以打开链接"))));
    }

    private static MessageProcessorInfo buildComponentRendererInfo() {
        Component description = Component.literal("（此组件无法被移除）将")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("mention").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("、"))
                .append(Component.literal("link").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("等特殊消息合并后转换为带有富文本的消息。"));

        return MessageProcessorInfo.of("component", description);
    }

    private static MessageProcessorInfo buildMentionNotifierInfo() {
        Component description = Component.literal("此为")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("mention").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("的附加组件，实现了为需要被提醒的玩家执行的特殊效果（"))
                .append(Component.literal("音效").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("、"))
                .append(Component.literal("在屏幕中显示").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("等）"));

        return MessageProcessorInfo.of("mention_notify", description)
                .addExample(new MessageProcessorInfo.Example("@all 兄弟们我挖到钻石辣！！！",
                        Component.literal("该消息被mention处理后会为除了发送者外的所有玩家额外触发")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("音效").withStyle(ChatFormatting.AQUA))
                                .append(Component.literal("和"))
                                .append(Component.literal("屏幕提示").withStyle(ChatFormatting.LIGHT_PURPLE))));
    }

    private static MessageProcessorInfo buildTeleportRequestProcessorInfo() {
        Component description = Component.literal("如果玩家在聊天窗口中只输入了有效的")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("玩家名称").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("，则会为其附加点击后传送到该玩家的能力"));

        return MessageProcessorInfo.of("teleport", description)
                .addExample(new MessageProcessorInfo.Example("Steven",
                        Component.literal("此消息会为").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("\"Steven\"").withStyle(ChatFormatting.AQUA))
                                .append(Component.literal("特殊显示，并且点击后可以传送到该玩家处"))));
    }

    private static MessageProcessorInfo buildHandItemParserInfo() {
        Component description = Component.literal("允许在聊天中展示玩家身上指定物品信息的能力，允许重复出现")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("\n允许使用以下格式：").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n[i]").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" - 主手 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[io]").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" - 副手").withStyle(ChatFormatting.GRAY));

        return MessageProcessorInfo.of("hand_item", description)
                .addExample(new MessageProcessorInfo.Example("兄弟们看看我的装备[i][io]，我的护甲[it][ij][ik][ix]",
                        Component.literal("此消息允许")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("重复使用").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("物品标记"))));
    }

    private static MessageProcessorInfo buildXaeroMapUtilProcessorInfo() {
        MutableComponent description = Component.literal("Xaero地图工具，使用方式：当你只在")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("聊天栏")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("中输入")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("任何在线玩家")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("的名字时，将会在消息末尾自动插入可点击添加的路径点")
                        .withStyle(ChatFormatting.GRAY));

        return MessageProcessorInfo.of("xaero_map_util", description)
                .addExample(new MessageProcessorInfo.Example("Steven",
                        Component.literal("此消息会为").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("\"Steven\"").withStyle(ChatFormatting.AQUA))
                                .append(Component.literal("后额外添加一条")
                                        .withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("特殊消息").withStyle(ChatFormatting.GOLD))
                ))
                .addExample(new MessageProcessorInfo.Example("任意前缀[name:\"test\", x:123, y:64, z:-456, dim:minecraft:overworld]任意后缀",
                        Component.literal("当消息中存在")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("JourneyMap")
                                        .withStyle(ChatFormatting.GOLD))
                                .append(Component.literal("格式的路径点时，将会在消息末尾自动插入可点击转换为xaero格式的路径点")
                                        .withStyle(ChatFormatting.GRAY)))
                );
    }

    private static MessageProcessorInfo buildJourneyMapUtilProcessorInfo() {
        MutableComponent description = Component.literal("JourneyMap工具，使用方式：当你只在")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("聊天栏")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("中输入")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("任何在线玩家")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("的名字时，将会在消息末尾自动插入可点击添加的路径点")
                        .withStyle(ChatFormatting.GRAY));
        return MessageProcessorInfo.of("journey_map_util", description)
                .addExample(new MessageProcessorInfo.Example("Steven",
                        Component.literal("此消息会为").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("\"Steven\"").withStyle(ChatFormatting.AQUA))
                                .append(Component.literal("后额外添加一条")
                                        .withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("特殊消息").withStyle(ChatFormatting.GOLD))
                ));
    }

    @Override
    public void onEvent(MinecraftServerEvents.PlayerChatEvent event) {
        Component newComponent = pipeline.processMessage(
                event.getPlayer(),
                event.getOriginalComponent().getString()
        );
        event.setComponent(newComponent);
    }
}