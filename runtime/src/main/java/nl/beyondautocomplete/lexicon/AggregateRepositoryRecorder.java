package nl.beyondautocomplete.lexicon;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;

@Recorder
public class AggregateRepositoryRecorder {
    public Supplier<Object> repositorySupplier(String repositoryInterfaceName, String aggregateClassName, String eventStreamName) {
        return () -> {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            try {
                Class<?> repositoryInterface = Class.forName(repositoryInterfaceName, true, classLoader);

                @SuppressWarnings("unchecked")
                Class<? extends AggregateRoot> aggregateClass =
                        (Class<? extends AggregateRoot>) Class.forName(aggregateClassName, true, classLoader);

                EventStore eventStore = Arc.container().instance(EventStore.class).get();

                return Proxy.newProxyInstance(
                        classLoader,
                        new Class<?>[]{repositoryInterface},
                        new AggregateRepositoryInvocationHandler(eventStore, aggregateClass, eventStreamName));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Unable to create aggregate repository for " + repositoryInterfaceName, e);
            }
        };
    }
}
