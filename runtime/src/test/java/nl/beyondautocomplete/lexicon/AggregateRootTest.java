package nl.beyondautocomplete.lexicon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateRootTest {

    record SampleCreated(String value) {
    }

    record SampleRenamed(String value) {
    }

    record UnhandledEvent() {
    }

    static class SampleAggregate extends AggregateRoot {
        private String id;
        private String value;

        @Override
        public String aggregateId() {
            return id;
        }

        String value() {
            return value;
        }

        @DomainEventHandler
        void apply(SampleCreated event) {
            this.id = "sample-1";
            this.value = event.value();
        }

        @DomainEventHandler
        void apply(SampleRenamed event) {
            this.value = event.value();
        }
    }

    @Test
    void recordEventInvokesMatchingHandlerAndIncrementsVersion() {
        var aggregate = new SampleAggregate();

        AggregateLifecycle.instance().emitDomainEvent(aggregate, new SampleCreated("first"));

        assertEquals("first", aggregate.value());
        assertEquals(1, aggregate.version());
    }

    @Test
    void recordEventAddsToPendingEvents() {
        var aggregate = new SampleAggregate();

        AggregateLifecycle.instance().emitDomainEvent(aggregate, new SampleCreated("first"));
        AggregateLifecycle.instance().emitDomainEvent(aggregate, new SampleRenamed("second"));

        assertEquals(2, aggregate.pendingEvents().size());
        assertEquals(new SampleCreated("first"), aggregate.pendingEvents().get(0));
        assertEquals(new SampleRenamed("second"), aggregate.pendingEvents().get(1));
    }

    @Test
    void replayEventDoesNotAddToPendingEventsButStillIncrementsVersion() {
        var aggregate = new SampleAggregate();

        aggregate.replayEvent(new SampleCreated("first"));
        aggregate.replayEvent(new SampleRenamed("second"));

        assertEquals("second", aggregate.value());
        assertEquals(2, aggregate.version());
        assertTrue(aggregate.pendingEvents().isEmpty());
    }

    @Test
    void markEventsAsCommittedClearsPendingEvents() {
        var aggregate = new SampleAggregate();
        AggregateLifecycle.instance().emitDomainEvent(aggregate, new SampleCreated("first"));

        aggregate.markEventsAsCommitted();

        assertTrue(aggregate.pendingEvents().isEmpty());
        assertEquals(1, aggregate.version());
    }

    @Test
    void throwsWhenNoHandlerMatchesTheEventType() {
        var aggregate = new SampleAggregate();

        assertThrows(DomainEventHandlerNotFoundException.class,
                () -> aggregate.replayEvent(new UnhandledEvent()));
    }
}
