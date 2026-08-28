package nl.beyondautocomplete.lexicon;

public final class AggregateLifecycle {
    private static final AggregateLifecycle INSTANCE = new AggregateLifecycle();

    private AggregateLifecycle() {
    }

    public static AggregateLifecycle instance() {
        return INSTANCE;
    }

    public void emitDomainEvent(AggregateRoot aggregate, Object event) {
        aggregate.recordEvent(event);
    }
}
