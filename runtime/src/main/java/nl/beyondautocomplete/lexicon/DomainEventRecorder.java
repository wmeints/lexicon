package nl.beyondautocomplete.lexicon;

import io.quarkus.runtime.annotations.Recorder;

import java.util.List;
import java.util.function.Supplier;

@Recorder
public class DomainEventRecorder {
    public Supplier<DomainEventRegistry> registrySupplier(List<DomainEventDescriptor> descriptors) {
        return () -> new DomainEventRegistry(descriptors);
    }
}
