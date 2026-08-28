package nl.beyondautocomplete.lexicon;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;

final class AggregateRepositoryInvocationHandler implements InvocationHandler {
    private final EventStore eventStore;
    private final Class<? extends AggregateRoot> aggregateClass;
    private final String aggregateType;

    AggregateRepositoryInvocationHandler(EventStore eventStore, Class<? extends AggregateRoot> aggregateClass, String aggregateType) {
        this.eventStore = eventStore;
        this.aggregateClass = aggregateClass;
        this.aggregateType = aggregateType;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "load" -> load((String) args[0]);
            case "save" -> {
                save((AggregateRoot) args[0]);
                yield null;
            }
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "AggregateRepository[" + aggregateType + "]";
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private AggregateRoot load(String aggregateId) {
        List<Object> events = eventStore.load(aggregateId, aggregateType);

        if (events.isEmpty()) {
            throw new AggregateNotFoundException(
                    "No " + aggregateType + " aggregate found with id " + aggregateId);
        }

        AggregateRoot aggregate = instantiate();
        events.forEach(aggregate::replayEvent);

        return aggregate;
    }

    private void save(AggregateRoot aggregate) {
        List<Object> pendingEvents = aggregate.pendingEvents();

        if (pendingEvents.isEmpty()) {
            return;
        }

        long expectedVersion = aggregate.version() - pendingEvents.size();

        eventStore.append(aggregate.aggregateId(), aggregateType, pendingEvents, expectedVersion);
        aggregate.markEventsAsCommitted();
    }

    private AggregateRoot instantiate() {
        try {
            var constructor = aggregateClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Aggregate " + aggregateClass.getName() + " needs a no-arg constructor", e);
        }
    }
}
