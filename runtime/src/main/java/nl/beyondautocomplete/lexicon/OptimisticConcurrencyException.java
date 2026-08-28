package nl.beyondautocomplete.lexicon;

public class OptimisticConcurrencyException extends RuntimeException {
    public OptimisticConcurrencyException(String message) {
        super(message);
    }
}
