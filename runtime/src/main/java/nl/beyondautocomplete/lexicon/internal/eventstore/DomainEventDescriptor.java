package nl.beyondautocomplete.lexicon.internal.eventstore;

public record DomainEventDescriptor(String logicalName, Class<?> eventClass) {
}
