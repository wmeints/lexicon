package nl.beyondautocomplete.lexicon.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class AggregateRepositoryBuildItem extends MultiBuildItem {
    private final String repositoryInterfaceName;
    private final String aggregateClassName;
    private final String eventStreamName;

    public AggregateRepositoryBuildItem(String repositoryInterfaceName, String aggregateClassName, String eventStreamName) {
        this.repositoryInterfaceName = repositoryInterfaceName;
        this.aggregateClassName = aggregateClassName;
        this.eventStreamName = eventStreamName;
    }

    public String getRepositoryInterfaceName() {
        return repositoryInterfaceName;
    }

    public String getAggregateClassName() {
        return aggregateClassName;
    }

    public String getEventStreamName() {
        return eventStreamName;
    }
}
