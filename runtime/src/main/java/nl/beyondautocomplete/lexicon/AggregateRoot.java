package nl.beyondautocomplete.lexicon;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AggregateRoot {
    private static final Map<Class<?>, Map<Class<?>, Method>> HANDLERS_BY_AGGREGATE_TYPE = new ConcurrentHashMap<>();

    private long version = 0;
    private final transient List<Object> pendingEvents = new ArrayList<>();

    public abstract String aggregateId();

    public final long version() {
        return version;
    }

    final List<Object> pendingEvents() {
        return List.copyOf(pendingEvents);
    }

    final void markEventsAsCommitted() {
        pendingEvents.clear();
    }

    final void recordEvent(Object event) {
        apply(event);
        pendingEvents.add(event);
    }

    final void replayEvent(Object event) {
        apply(event);
    }

    private void apply(Object event) {
        Method handler = resolveHandler(getClass(), event.getClass());

        try {
            handler.invoke(this, event);
        } catch (InvocationTargetException e) {
            throw unchecked(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to invoke domain event handler " + handler, e);
        }

        version++;
    }

    private static RuntimeException unchecked(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }

        return new RuntimeException(cause);
    }

    private static Method resolveHandler(Class<?> aggregateType, Class<?> eventType) {
        Map<Class<?>, Method> handlers = HANDLERS_BY_AGGREGATE_TYPE.computeIfAbsent(
                aggregateType, AggregateRoot::discoverHandlers);

        Method handler = handlers.get(eventType);

        if (handler == null) {
            throw new DomainEventHandlerNotFoundException(
                    "No @DomainEventHandler method found on " + aggregateType.getName()
                            + " for event " + eventType.getName());
        }

        return handler;
    }

    private static Map<Class<?>, Method> discoverHandlers(Class<?> aggregateType) {
        Map<Class<?>, Method> handlers = new HashMap<>();

        for (Class<?> type = aggregateType; type != null && type != AggregateRoot.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(DomainEventHandler.class)) {
                    continue;
                }

                if (method.getParameterCount() != 1) {
                    throw new IllegalStateException(
                            "@DomainEventHandler method " + method + " must declare exactly one parameter");
                }

                method.setAccessible(true);
                handlers.putIfAbsent(method.getParameterTypes()[0], method);
            }
        }

        return handlers;
    }
}
