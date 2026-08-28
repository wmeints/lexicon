package nl.beyondautocomplete.lexicon.deployment;

import io.quarkus.test.QuarkusExtensionTest;
import nl.beyondautocomplete.lexicon.AggregateRepository;
import nl.beyondautocomplete.lexicon.AggregateRoot;
import nl.beyondautocomplete.lexicon.EventStream;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DuplicateEventStreamValidationTest {

    @EventStream("duplicate")
    public static class FirstAggregate extends AggregateRoot {
        @Override
        public String aggregateId() {
            return "first-1";
        }
    }

    @EventStream("duplicate")
    public static class SecondAggregate extends AggregateRoot {
        @Override
        public String aggregateId() {
            return "second-1";
        }
    }

    public interface FirstAggregateRepository extends AggregateRepository<FirstAggregate> {
    }

    public interface SecondAggregateRepository extends AggregateRepository<SecondAggregate> {
    }

    @RegisterExtension
    static final QuarkusExtensionTest TEST = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            FirstAggregate.class, FirstAggregateRepository.class,
                            SecondAggregate.class, SecondAggregateRepository.class))
            .overrideConfigKey("quarkus.hibernate-orm.enabled", "false")
            .setExpectedException(IllegalStateException.class);

    @Test
    void buildFailsBecauseTwoAggregatesShareTheSameEventStreamName() {
    }
}
