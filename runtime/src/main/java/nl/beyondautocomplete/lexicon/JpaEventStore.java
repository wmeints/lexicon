package nl.beyondautocomplete.lexicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class JpaEventStore implements EventStore {
    private final ObjectMapper objectMapper;
    private final DomainEventRegistry domainEventRegistry;

    public JpaEventStore(ObjectMapper objectMapper, DomainEventRegistry domainEventRegistry) {
        this.objectMapper = objectMapper;
        this.domainEventRegistry = domainEventRegistry;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRED)
    public void append(String aggregateId, String aggregateType, List<Object> domainEvents, long expectedVersion) {
        var recordVersion = EventRecord.nextVersion(aggregateType, aggregateId);
        var currentVersion = recordVersion - 1;

        if (currentVersion != expectedVersion) {
            throw new OptimisticConcurrencyException(
                    "Expected version " + expectedVersion + " for aggregate " + aggregateType + "/" + aggregateId +
                            " but the event store has version " + currentVersion);
        }

        for(var domainEvent : domainEvents) {
            var record = new EventRecord();

            record.aggregateId = aggregateId;
            record.aggregateType = aggregateType;
            record.eventData = serializeEventData(domainEvent);
            record.eventType = domainEventType(domainEvent);
            record.timestamp = LocalDateTime.now(UTC);
            record.version = recordVersion++;

            record.persist();
        }
    }

    @Override
    public List<Object> load(String aggregateId, String aggregateType) {
        var records = EventRecord.listByAggregate(aggregateType, aggregateId);
        return records.stream().map(this::deserializeRecord).toList();
    }

    private String serializeEventData(Object eventData) {
        try {
            return objectMapper.writeValueAsString(eventData);
        } catch(JsonProcessingException e) {
            throw new DomainEventSerializationException("Unable to serialize event payload", e);
        }
    }

    private Object deserializeRecord(EventRecord record) {
        try {
            var eventType = domainEventRegistry.resolve(record.eventType);
            return objectMapper.readValue(record.eventData, eventType);
        } catch(JsonProcessingException e) {
            throw new DomainEventSerializationException("Unable to deserialize event payload", e);
        }
    }

    private String domainEventType(Object eventData) {
        return domainEventRegistry.nameOf(eventData.getClass());
    }
}
