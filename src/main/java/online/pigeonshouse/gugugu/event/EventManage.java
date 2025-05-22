package online.pigeonshouse.gugugu.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManage<T extends BaseEvent> {
    private final List<EventCallback<T>> callbacks = new CopyOnWriteArrayList<>();
    private final Class<T> sourceClass;

    private EventManage(Class<T> sourceClass) {
        this.sourceClass = sourceClass;
    }

    public static <T extends BaseEvent> EventManage<T> of(Class<T> sourceClass) {
        return new EventManage<>(sourceClass);
    }

    public void addCallback(EventCallback<T> callback) {
        callbacks.add(callback);
    }

    public void removeCallback(EventCallback<T> callback) {
        callbacks.remove(callback);
    }

    public void dispatch(T event) {
        for (EventCallback<T> callback : callbacks) {
            callback.onEvent(event);
        }
    }

    public Class<T> getEventType() {
        return sourceClass;
    }
}
