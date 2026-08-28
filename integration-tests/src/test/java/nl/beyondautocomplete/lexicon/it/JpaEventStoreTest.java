package nl.beyondautocomplete.lexicon.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.beyondautocomplete.lexicon.DomainEvent;
import nl.beyondautocomplete.lexicon.EventStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class JpaEventStoreTest {

    @DomainEvent("it.order-placed")
    public record OrderPlaced(String orderId) {
    }

    @DomainEvent("it.item-added")
    public record ItemAdded(String sku, int quantity) {
    }

    @Inject
    EventStore eventStore;

    @Test
    void savesAndLoadsEventsInOrderForAnAggregate() {
        var aggregateId = UUID.randomUUID().toString();

        eventStore.append(aggregateId, "Order", List.of(
                new OrderPlaced("SKU-1"),
                new ItemAdded("SKU-2", 3)));

        var events = eventStore.load(aggregateId, "Order");

        assertEquals(List.of(new OrderPlaced("SKU-1"), new ItemAdded("SKU-2", 3)), events);
    }

    @Test
    void appendsNewEventsAfterExistingOnesForTheSameAggregate() {
        var aggregateId = UUID.randomUUID().toString();

        eventStore.append(aggregateId, "Order", List.of(new OrderPlaced("SKU-1")));
        eventStore.append(aggregateId, "Order", List.of(new ItemAdded("SKU-2", 1)));

        var events = eventStore.load(aggregateId, "Order");

        assertEquals(List.of(new OrderPlaced("SKU-1"), new ItemAdded("SKU-2", 1)), events);
    }

    @Test
    void isolatesEventsPerAggregate() {
        var firstAggregateId = UUID.randomUUID().toString();
        var secondAggregateId = UUID.randomUUID().toString();

        eventStore.append(firstAggregateId, "Order", List.of(new OrderPlaced("A")));
        eventStore.append(secondAggregateId, "Order", List.of(new OrderPlaced("B"), new ItemAdded("C", 2)));

        assertEquals(List.of(new OrderPlaced("A")), eventStore.load(firstAggregateId, "Order"));
        assertEquals(List.of(new OrderPlaced("B"), new ItemAdded("C", 2)), eventStore.load(secondAggregateId, "Order"));
    }

    @Test
    void returnsEmptyListForUnknownAggregate() {
        assertTrue(eventStore.load(UUID.randomUUID().toString(), "Order").isEmpty());
    }
}
