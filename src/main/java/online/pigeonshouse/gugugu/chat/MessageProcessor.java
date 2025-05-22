package online.pigeonshouse.gugugu.chat;

public interface MessageProcessor extends PriorityConstants {
    default int getPriority() {
        return NORMAL;
    }

    void process(MessageContext context);

    default void test(MessageContext context) {
        process(context);
    }

    /**
     * 在执行测试时需要附加的特殊处理器
     */
    default String[] testProcessors() {
        return new String[0];
    }
}