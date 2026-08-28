package nl.beyondautocomplete.lexicon;

public interface AggregateRepository<T extends AggregateRoot> {
    T load(String aggregateId);
    void save(T aggregate);
}
