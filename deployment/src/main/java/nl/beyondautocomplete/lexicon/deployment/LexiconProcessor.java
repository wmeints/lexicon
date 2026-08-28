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
import nl.beyondautocomplete.lexicon.internal.eventstore.DomainEventDescriptor;
import nl.beyondautocomplete.lexicon.internal.eventstore.DomainEventRecorder;
import nl.beyondautocomplete.lexicon.internal.eventstore.DomainEventRegistry;
import nl.beyondautocomplete.lexicon.internal.eventstore.JpaEventStore;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class LexiconProcessor {

    private static final String FEATURE = "lexicon";
    private static final DotName DOMAIN_EVENT_TYPE = DotName.createSimple(DomainEvent.class.getName());
    private static final DotName EVENT_STREAM_TYPE = DotName.createSimple(EventStream.class.getName());
    private static final DotName AGGREGATE_ROOT_TYPE = DotName.createSimple(AggregateRoot.class.getName());
    private static final DotName AGGREGATE_REPOSITORY_TYPE = DotName.createSimple(AggregateRepository.class.getName());

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

    @BuildStep
    void discoverAggregateRepositories(CombinedIndexBuildItem combinedIndex,
                                        BuildProducer<AggregateRepositoryBuildItem> aggregateRepositories,
                                        BuildProducer<ReflectiveClassBuildItem> reflective) {

        IndexView index = combinedIndex.getIndex();

        Map<DotName, String> eventStreamNamesByClass = new HashMap<>();
        for (AnnotationInstance annotation : index.getAnnotations(EVENT_STREAM_TYPE)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }

            eventStreamNamesByClass.put(annotation.target().asClass().name(), annotation.value().asString());
        }

        Set<DotName> aggregateRootClasses = new HashSet<>();
        for (ClassInfo classInfo : index.getAllKnownSubclasses(AGGREGATE_ROOT_TYPE)) {
            aggregateRootClasses.add(classInfo.name());
        }

        Set<String> collectedEventStreamNames = new HashSet<>();

        for (ClassInfo repositoryInterface : index.getAllKnownSubinterfaces(AGGREGATE_REPOSITORY_TYPE)) {
            DotName aggregateClassName = resolveAggregateType(repositoryInterface);

            if (!aggregateRootClasses.contains(aggregateClassName)) {
                throw new IllegalStateException(
                        "Aggregate class " + aggregateClassName + " referenced by " + repositoryInterface.name()
                                + " does not extend AggregateRoot");
            }

            String eventStreamName = eventStreamNamesByClass.get(aggregateClassName);

            if (eventStreamName == null) {
                throw new IllegalStateException(
                        "Aggregate class " + aggregateClassName + " must be annotated with @EventStream");
            }

            if (!collectedEventStreamNames.add(eventStreamName)) {
                throw new IllegalStateException("Duplicate event stream name: " + eventStreamName);
            }

            aggregateRepositories.produce(new AggregateRepositoryBuildItem(
                    repositoryInterface.name().toString(),
                    aggregateClassName.toString(),
                    eventStreamName));

            reflective.produce(ReflectiveClassBuildItem
                    .builder(aggregateClassName.toString())
                    .constructors().methods()
                    .reason("Aggregate instantiation and event replay")
                    .build());
        }
    }

    private static DotName resolveAggregateType(ClassInfo repositoryInterface) {
        for (Type interfaceType : repositoryInterface.interfaceTypes()) {
            if (interfaceType.name().equals(AGGREGATE_REPOSITORY_TYPE)
                    && interfaceType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                return interfaceType.asParameterizedType().arguments().get(0).name();
            }
        }

        throw new IllegalStateException(
                repositoryInterface.name() + " must directly extend AggregateRepository<T> with a concrete type argument");
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void aggregateRepositoryBeans(List<AggregateRepositoryBuildItem> aggregateRepositories,
                                   AggregateRepositoryRecorder recorder,
                                   BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        for (var buildItem : aggregateRepositories) {
            syntheticBeans.produce(SyntheticBeanBuildItem
                    .configure(DotName.createSimple(buildItem.getRepositoryInterfaceName()))
                    .scope(Singleton.class)
                    .unremovable()
                    .setRuntimeInit()
                    .supplier(recorder.repositorySupplier(
                            buildItem.getRepositoryInterfaceName(),
                            buildItem.getAggregateClassName(),
                            buildItem.getEventStreamName()))
                    .done());
        }
    }
}
