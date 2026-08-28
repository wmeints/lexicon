package nl.beyondautocomplete.lexicon;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name="event_streams", indexes = {
        @Index(name="idx_event_streams", columnList = "aggregate_id, version", unique = true),
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
    @Column(name="aggregate_type", nullable=false, columnDefinition = "varchar(150)")
    public String aggregateType;

    @Column(name="aggregate_id", nullable=false, columnDefinition = "varchar(40)")
    public String aggregateId;

    @Column(name="event_type", nullable = false, columnDefinition = "varchar(150)")
    public String eventType;

    @Column(name="version",nullable = false, columnDefinition = "bigint")
    public Long version;

    @Column(name="event_data",nullable = false, columnDefinition = "text")
    public String eventData;

    @Column(name="timestamp",nullable = false, columnDefinition = "timestamp")
    public LocalDateTime timestamp;

    public static long nextVersion(String aggregateType, String aggregateId) {
        return count("#EventRecord.nextVersion", aggregateId, aggregateType) + 1;
    }

    public static List<EventRecord> listByAggregate(String aggregateType, String aggregateId) {
        return list("#EventRecord.listByAggregate", aggregateId, aggregateType);
    }
}
