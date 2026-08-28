package nl.beyondautocomplete.lexicon.deployment;

import io.quarkus.test.QuarkusExtensionTest;
import nl.beyondautocomplete.lexicon.AggregateRepository;
import nl.beyondautocomplete.lexicon.AggregateRoot;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MissingEventStreamValidationTest {

    public static class NoStreamAggregate extends AggregateRoot {
        @Override
        public String aggregateId() {
            return "no-stream-1";
        }
    }

    public interface NoStreamAggregateRepository extends AggregateRepository<NoStreamAggregate> {
    }

    @RegisterExtension
    static final QuarkusExtensionTest TEST = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(NoStreamAggregate.class, NoStreamAggregateRepository.class))
            .overrideConfigKey("quarkus.hibernate-orm.enabled", "false")
            .setExpectedException(IllegalStateException.class);

    @Test
    void buildFailsBecauseTheAggregateHasNoEventStreamAnnotation() {
    }
}
