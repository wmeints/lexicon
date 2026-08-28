package nl.beyondautocomplete.lexicon.internal.eventstore;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(indexes = {
        @Index(name="idx_event_streams", columnList = "aggregateId, version", unique = true),
})
@NamedQueries({
        @NamedQuery(
                name = "EventRecord.nextVersion",
                query = "select count(*) from EventRecord where aggregateId = ?1 and aggregateType = ?2"
        ),
        @NamedQuery(
                name = "EventRecord.listByAggregate",
                query = "from EventRecord where aggregateId = ?1 and aggregateType = ?2 order by version"
        )
})
public class EventRecord extends PanacheEntity {
    @Column(nullable=false, length=150)
    public String aggregateType;

    @Column(nullable=false, length=40)
    public String aggregateId;

    @Column(nullable = false, length=150)
    public String eventType;

    @Column(nullable = false)
    public Long version;

    @Lob
    @Column(nullable = false)
    public String eventData;

    @Column(nullable = false)
    public LocalDateTime timestamp;

    public static long nextVersion(String aggregateType, String aggregateId) {
        return count("#EventRecord.nextVersion", aggregateId, aggregateType) + 1;
    }

    public static List<EventRecord> listByAggregate(String aggregateType, String aggregateId) {
        return list("#EventRecord.listByAggregate", aggregateId, aggregateType);
    }
}
