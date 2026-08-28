# Creating your first aggregate

This guide walks through building a small aggregate with Lexicon: a
`Vehicle` that can be registered and later transferred to a new owner. It
assumes you've already added the extension to your project as described in
[Installation](01-installation.md).

## 1. Configure a datasource

Lexicon stores events using Hibernate ORM, so your application needs a
datasource and a JDBC driver. Add a driver extension (H2 is a good choice
for getting started):

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-h2</artifactId>
</dependency>
```

And configure the datasource in `application.properties`:

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:mydb;DB_CLOSE_DELAY=-1
quarkus.hibernate-orm.database.generation=drop-and-create
```

`drop-and-create` is fine while you're experimenting; use proper migrations
(e.g. Flyway) once you move towards production.

## 2. Define your commands

Commands describe what a caller wants to happen. Lexicon doesn't require
commands to implement any interface — plain records work fine:

```java
public record RegisterVehicle(String licensePlate, String ownerId) {
}

public record ChangeOwner(String ownerId) {
}
```

## 3. Define your domain events

Domain events describe what actually happened, and are what gets persisted
to the event store. Annotate each event with `@DomainEvent`, giving it a
stable logical name — this name is stored alongside the event data, so
change it only if you're prepared to migrate existing history:

```java
import nl.beyondautocomplete.lexicon.DomainEvent;

@DomainEvent("vehicle-registered")
public record VehicleRegistered(String licensePlate, String ownerId) {
}

@DomainEvent("vehicle-owner-changed")
public record OwnerChanged(String ownerId) {
}
```

Behind the scenes, Lexicon registers these classes for JSON
serialization automatically — you don't need to add any reflection
configuration yourself.

## 4. Create the aggregate

An aggregate is a class that extends `AggregateRoot` and is annotated with
`@EventStream`, which gives the aggregate's event stream a stable logical
name (used to distinguish it from other aggregate types in the event
store):

```java
import nl.beyondautocomplete.lexicon.AggregateRoot;
import nl.beyondautocomplete.lexicon.EventStream;

@EventStream("vehicle")
public class Vehicle extends AggregateRoot {
    private String licensePlate;
    private String ownerId;

    @Override
    public String aggregateId() {
        return licensePlate;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getOwnerId() {
        return ownerId;
    }
}
```

`aggregateId()` must return the value that uniquely identifies this
aggregate instance — it's what you'll use to load the aggregate back later.

## 5. Add command-handling methods

A command-handling method takes a command, decides whether it's valid, and
emits one or more domain events through `AggregateLifecycle` to record what
happened. It should never mutate the aggregate's fields directly — state
changes only happen in response to events (see the next step).

Creating a brand-new aggregate is usually done with a static factory
method:

```java
public static Vehicle register(RegisterVehicle cmd) {
    Vehicle vehicle = new Vehicle();
    var event = new VehicleRegistered(cmd.licensePlate(), cmd.ownerId());
    AggregateLifecycle.instance().emitDomainEvent(vehicle, event);
    return vehicle;
}
```

Commands against an existing aggregate are regular instance methods:

```java
public void changeOwner(ChangeOwner cmd) {
    AggregateLifecycle.instance().emitDomainEvent(this, new OwnerChanged(cmd.ownerId()));
}
```

This is also where you'd add validation — for example, throwing an
exception if `cmd.ownerId()` is blank — before emitting the event.

## 6. Add domain event handlers

Every event a command handler emits must have a matching handler method,
annotated with `@DomainEventHandler`, that applies the event to the
aggregate's state. These methods are called both when a new event is
emitted and when past events are replayed to rebuild an aggregate from
history, so they must be side-effect free beyond updating fields:

```java
import nl.beyondautocomplete.lexicon.DomainEventHandler;

@DomainEventHandler
void apply(VehicleRegistered evt) {
    this.licensePlate = evt.licensePlate();
    this.ownerId = evt.ownerId();
}

@DomainEventHandler
void apply(OwnerChanged evt) {
    this.ownerId = evt.ownerId();
}
```

Lexicon matches handler methods to events by their single parameter type,
so each event type needs exactly one `@DomainEventHandler` method per
aggregate.

## 7. Add a repository

To load and save aggregates, declare an interface that extends
`AggregateRepository<T>`. Lexicon generates the implementation for you at
build time — you don't write one yourself:

```java
import nl.beyondautocomplete.lexicon.AggregateRepository;

public interface VehicleRepository extends AggregateRepository<Vehicle> {
}
```

## 8. Use the aggregate

Inject the repository like any other CDI bean:

```java
@Inject
VehicleRepository vehicleRepository;

void example() {
    var vehicle = Vehicle.register(new RegisterVehicle("AA-123-B", "owner-1"));
    vehicleRepository.save(vehicle);

    var loaded = vehicleRepository.load("AA-123-B");
    loaded.changeOwner(new ChangeOwner("owner-2"));
    vehicleRepository.save(loaded);
}
```

`load` replays the aggregate's full event history to rebuild its current
state; `save` appends any newly emitted events. If two callers load the
same aggregate and both try to save changes based on the same starting
version, the second `save` call fails with an
`OptimisticConcurrencyException` — catch it and retry if that's a
scenario you need to handle.

## Putting it all together

```java
public record RegisterVehicle(String licensePlate, String ownerId) {
}

public record ChangeOwner(String ownerId) {
}

@DomainEvent("vehicle-registered")
public record VehicleRegistered(String licensePlate, String ownerId) {
}

@DomainEvent("vehicle-owner-changed")
public record OwnerChanged(String ownerId) {
}

@EventStream("vehicle")
public class Vehicle extends AggregateRoot {
    private String licensePlate;
    private String ownerId;

    @Override
    public String aggregateId() {
        return licensePlate;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public static Vehicle register(RegisterVehicle cmd) {
        Vehicle vehicle = new Vehicle();
        var event = new VehicleRegistered(cmd.licensePlate(), cmd.ownerId());
        AggregateLifecycle.instance().emitDomainEvent(vehicle, event);
        return vehicle;
    }

    public void changeOwner(ChangeOwner cmd) {
        AggregateLifecycle.instance().emitDomainEvent(this, new OwnerChanged(cmd.ownerId()));
    }

    @DomainEventHandler
    void apply(VehicleRegistered evt) {
        this.licensePlate = evt.licensePlate();
        this.ownerId = evt.ownerId();
    }

    @DomainEventHandler
    void apply(OwnerChanged evt) {
        this.ownerId = evt.ownerId();
    }
}

public interface VehicleRepository extends AggregateRepository<Vehicle> {
}
```
