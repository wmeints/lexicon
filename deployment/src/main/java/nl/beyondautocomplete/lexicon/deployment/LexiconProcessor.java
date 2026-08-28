package nl.beyondautocomplete.lexicon.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import jakarta.inject.Singleton;
import nl.beyondautocomplete.lexicon.*;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class LexiconProcessor {

    private static final String FEATURE = "lexicon";
    private static final DotName DOMAIN_EVENT_TYPE = DotName.createSimple(DomainEvent.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void discoverDomainEvents(CombinedIndexBuildItem combinedIndex,
                              BuildProducer<DomainEventBuildItem> domainEvents,
                              BuildProducer<ReflectiveClassBuildItem> reflective) {

        for(AnnotationInstance annotation : combinedIndex.getIndex().getAnnotations(DOMAIN_EVENT_TYPE)) {
            if(annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }

            ClassInfo eventClass = annotation.target().asClass();
            String logicalName = annotation.value().asString();

            domainEvents.produce(new DomainEventBuildItem(logicalName, eventClass.name().toString()));

            reflective.produce(ReflectiveClassBuildItem
                    .builder(eventClass.name().toString())
                    .constructors().methods().fields()
                    .reason("Jackson (de)serialization of @DomainEvent")
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    SyntheticBeanBuildItem domainEventRegistry(List<DomainEventBuildItem> domainEventBuildItems, DomainEventRecorder recorder, RecorderContext recorderContext) {
        List<DomainEventDescriptor> domainEventDescriptors = new ArrayList<>();
        Set<String> collectedDomainEventNames = new HashSet<>();

        for(var buildItem : domainEventBuildItems) {
            if(collectedDomainEventNames.contains(buildItem.getLogicalName())) {
                throw new IllegalStateException("Duplicate domain event name: " + buildItem.getLogicalName());
            }

            domainEventDescriptors.add(new DomainEventDescriptor(
                    buildItem.getLogicalName(),
                    recorderContext.classProxy(buildItem.getEventClassName())));

            collectedDomainEventNames.add(buildItem.getLogicalName());
        }

        return SyntheticBeanBuildItem
                .configure(DomainEventRegistry.class)
                .scope(Singleton.class)
                .unremovable()
                .supplier(recorder.registrySupplier(domainEventDescriptors))
                .done();
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem
                .builder()
                .addBeanClasses(JpaEventStore.class)
                .setUnremovable()
                .build();
    }
}
