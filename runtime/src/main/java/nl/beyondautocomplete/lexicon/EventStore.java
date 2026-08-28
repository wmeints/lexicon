package nl.beyondautocomplete.lexicon;

import java.util.List;

public interface EventStore {
    void append(String aggregateId, String aggregateType, List<Object> domainEvents);
    List<Object> load(String aggregateId, String aggregateType);
}
