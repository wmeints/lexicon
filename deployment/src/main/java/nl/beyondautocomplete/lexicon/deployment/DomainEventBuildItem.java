package nl.beyondautocomplete.lexicon.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class DomainEventBuildItem extends MultiBuildItem {
    private final String logicalName;
    private final String eventClassName;

    public DomainEventBuildItem(String logicalName, String eventClassName) {
        this.logicalName = logicalName;
        this.eventClassName = eventClassName;
    }

    public String getLogicalName() {
        return logicalName;
    }

    public String getEventClassName() {
        return eventClassName;
    }
}
