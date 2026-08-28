package nl.beyondautocomplete.lexicon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainEventRegistryTest {

    @DomainEvent("test.sample-created")
    record SampleEvent(String value) {
    }

    @DomainEvent("test.other")
    record OtherEvent() {
    }

    @Test
    void resolvesEventClassByLogicalName() {
        var registry = new DomainEventRegistry(List.of(
                new DomainEventDescriptor("test.sample-created", SampleEvent.class)));

        assertEquals(SampleEvent.class, registry.resolve("test.sample-created"));
    }

    @Test
    void resolvesLogicalNameByEventClass() {
        var registry = new DomainEventRegistry(List.of(
                new DomainEventDescriptor("test.sample-created", SampleEvent.class)));

        assertEquals("test.sample-created", registry.nameOf(SampleEvent.class));
    }

    @Test
    void keepsMultipleDomainEventsSeparate() {
        var registry = new DomainEventRegistry(List.of(
                new DomainEventDescriptor("test.sample-created", SampleEvent.class),
                new DomainEventDescriptor("test.other", OtherEvent.class)));

        assertEquals(SampleEvent.class, registry.resolve("test.sample-created"));
        assertEquals(OtherEvent.class, registry.resolve("test.other"));
        assertEquals("test.sample-created", registry.nameOf(SampleEvent.class));
        assertEquals("test.other", registry.nameOf(OtherEvent.class));
    }

    @Test
    void throwsWhenLogicalNameIsUnknown() {
        var registry = new DomainEventRegistry(List.of());

        assertThrows(UnknownDomainEventTypeException.class, () -> registry.resolve("does.not-exist"));
    }

    @Test
    void throwsWhenEventClassIsUnknown() {
        var registry = new DomainEventRegistry(List.of());

        assertThrows(UnknownDomainEventTypeException.class, () -> registry.nameOf(SampleEvent.class));
    }
}
