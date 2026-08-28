package nl.beyondautocomplete.lexicon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateRepositoryInvocationHandlerTest {

    record SampleCreated(String id, String value) {
    }

    record SampleRenamed(String value) {
    }

    public static class SampleAggregate extends AggregateRoot {
        private String id;
        private String value;

        @Override
        public String aggregateId() {
            return id;
        }

        String value() {
            return value;
        }

        public static SampleAggregate create(String id, String value) {
            var aggregate = new SampleAggregate();
            AggregateLifecycle.instance().emitDomainEvent(aggregate, new SampleCreated(id, value));
            return aggregate;
        }

        public void rename(String value) {
            AggregateLifecycle.instance().emitDomainEvent(this, new SampleRenamed(value));
        }

        @DomainEventHandler
        void apply(SampleCreated event) {
            this.id = event.id();
            this.value = event.value();
        }

        @DomainEventHandler
        void apply(SampleRenamed event) {
            this.value = event.value();
        }
    }

    public interface SampleRepository extends AggregateRepository<SampleAggregate> {
    }

    private static class InMemoryEventStore implements EventStore {
        private final Map<String, List<Object>> streams = new HashMap<>();

        @Override
        public void append(String aggregateId, String aggregateType, List<Object> domainEvents, long expectedVersion) {
            var stream = streams.computeIfAbsent(aggregateId, id -> new ArrayList<>());

            if (stream.size() != expectedVersion) {
                throw new OptimisticConcurrencyException("version mismatch");
            }

            stream.addAll(domainEvents);
        }

        @Override
        public List<Object> load(String aggregateId, String aggregateType) {
            return streams.getOrDefault(aggregateId, List.of());
        }
    }

    private SampleRepository newRepository(EventStore eventStore) {
        return (SampleRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{SampleRepository.class},
                new AggregateRepositoryInvocationHandler(eventStore, SampleAggregate.class, "sample"));
    }

    @Test
    void savePersistsPendingEventsAndMarksThemCommitted() {
        var eventStore = new InMemoryEventStore();
        var repository = newRepository(eventStore);
        var aggregate = SampleAggregate.create("sample-1", "first");

        repository.save(aggregate);

        assertEquals(List.of(new SampleCreated("sample-1", "first")), eventStore.load("sample-1", "sample"));
        assertTrue(aggregate.pendingEvents().isEmpty());
    }

    @Test
    void saveWithNoPendingEventsDoesNotTouchTheEventStore() {
        var eventStore = new InMemoryEventStore();
        var repository = newRepository(eventStore);
        var aggregate = SampleAggregate.create("sample-1", "first");
        aggregate.markEventsAsCommitted();

        repository.save(aggregate);

        assertTrue(eventStore.load("sample-1", "sample").isEmpty());
    }

    @Test
    void loadReplaysStoredEventsIntoANewAggregateInstance() {
        var eventStore = new InMemoryEventStore();
        var repository = newRepository(eventStore);

        var created = SampleAggregate.create("sample-1", "first");
        repository.save(created);
        created.rename("second");
        repository.save(created);

        var loaded = repository.load("sample-1");

        assertEquals("second", loaded.value());
        assertEquals(2, loaded.version());
        assertTrue(loaded.pendingEvents().isEmpty());
    }

    @Test
    void loadThrowsWhenAggregateIsUnknown() {
        var repository = newRepository(new InMemoryEventStore());

        assertThrows(AggregateNotFoundException.class, () -> repository.load("does-not-exist"));
    }

    @Test
    void saveComputesExpectedVersionFromAggregateVersionAndPendingEventCount() {
        var eventStore = new InMemoryEventStore();
        var repository = newRepository(eventStore);

        var aggregate = SampleAggregate.create("sample-1", "first");
        repository.save(aggregate);

        var stale = repository.load("sample-1");
        var fresh = repository.load("sample-1");

        fresh.rename("second");
        repository.save(fresh);

        stale.rename("conflicting");
        assertThrows(OptimisticConcurrencyException.class, () -> repository.save(stale));
    }
}
