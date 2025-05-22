package online.pigeonshouse.gugugu.event;

@FunctionalInterface
public interface EventCallback<T extends BaseEvent> {
    /**
     * 事件回调，会在其他线程执行
     *
     * @param event 事件
     */
    void onEvent(T event);
}
