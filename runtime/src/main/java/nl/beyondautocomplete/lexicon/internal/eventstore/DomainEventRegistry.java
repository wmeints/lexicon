package nl.beyondautocomplete.lexicon.internal.eventstore;

import nl.beyondautocomplete.lexicon.UnknownDomainEventTypeException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DomainEventRegistry {
    private final Map<String, Class<?>> byName;
    private final Map<Class<?>, String> byClass;

    public DomainEventRegistry(List<DomainEventDescriptor> descriptors) {
        byName = new HashMap<>();
        byClass = new HashMap<>();

        descriptors.forEach((entry) -> {
            byName.put(entry.logicalName(), entry.eventClass());
            byClass.put(entry.eventClass(), entry.logicalName());
        });
    }

    public Class<?> resolve(String logicalName) {
        var eventClass = byName.get(logicalName);

        if (eventClass == null) {
            throw new UnknownDomainEventTypeException(String.format("Unknown logical name: %s", logicalName));
        }

        return eventClass;
    }

    public String nameOf(Class<?> eventClass) {
        String name = byClass.get(eventClass);

        if (name == null) {
            throw new UnknownDomainEventTypeException("Unknown event class: " + eventClass);
        }

        return name;
    }
}
