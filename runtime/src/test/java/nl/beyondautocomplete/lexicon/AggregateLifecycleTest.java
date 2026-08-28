package nl.beyondautocomplete.lexicon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AggregateLifecycleTest {

    record SomethingHappened() {
    }

    static class SampleAggregate extends AggregateRoot {
        private boolean handled;

        @Override
        public String aggregateId() {
            return "sample-1";
        }

        @DomainEventHandler
        void apply(SomethingHappened event) {
            this.handled = true;
        }
    }

    @Test
    void instanceAlwaysReturnsTheSameSingleton() {
        assertSame(AggregateLifecycle.instance(), AggregateLifecycle.instance());
    }

    @Test
    void emitDomainEventRecordsTheEventOnTheAggregate() {
        var aggregate = new SampleAggregate();

        AggregateLifecycle.instance().emitDomainEvent(aggregate, new SomethingHappened());

        assertEquals(1, aggregate.pendingEvents().size());
        assertEquals(1, aggregate.version());
    }
}
