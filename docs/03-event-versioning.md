# Event versioning

Lexicon identifies events and aggregates in storage by the *logical names*
you assign with `@DomainEvent("...")` and `@EventStream("...")` — never by
the Java class name. Every row in the `event_streams` table stores the
logical name in `event_type`, and every query for an aggregate's history
filters on the logical name in `aggregate_type`:

```java
@DomainEvent("vehicle-registered")
public record VehicleRegistered(String licensePlate, String ownerId) {
}

@EventStream("vehicle")
public class Vehicle extends AggregateRoot {
    // ...
}
```

That indirection is what makes renaming Java classes safe, and what makes
renaming the logical names themselves risky. Lexicon has no built-in
mechanism for aliasing an old name to a new one or upcasting old payloads —
once a name is written to the event store, treat it as permanent.

## Renaming an event

**Renaming the Java class or record is always safe.** The class name never
appears in storage — only the `@DomainEvent` value does. Rename
`VehicleRegistered` to `VehicleWasRegistered` freely, as long as you leave
the annotation's logical name untouched:

```java
@DomainEvent("vehicle-registered") // unchanged
public record VehicleWasRegistered(String licensePlate, String ownerId) {
}
```

**Changing the logical name is not safe** once events with the old name
have been persisted. `DomainEventRegistry` resolves each stored
`event_type` to exactly one class, using whatever logical name is currently
declared in code. If you change `@DomainEvent("vehicle-registered")` to
`@DomainEvent("vehicle-registered-v2")`, every previously stored event with
`event_type = 'vehicle-registered'` becomes unresolvable, and loading any
aggregate with that history in it throws `UnknownDomainEventTypeException`.

Logical names must also stay unique across your whole application — the
build fails with `Duplicate domain event name` if two `@DomainEvent`
classes declare the same value, so you can't work around this by having two
classes share a name either.

If you genuinely need to correct a logical name (e.g. a typo shipped to
production), the only way to do it without losing history is a manual data
migration that rewrites the stored value directly:

```sql
UPDATE event_streams
SET event_type = 'vehicle-registered'
WHERE event_type = 'vehicle-registerd';
```

Run this once, then update the `@DomainEvent` annotation in code to match.
Because there's no framework support for this, treat it as an exceptional
fix rather than a normal part of your workflow — pick logical names
carefully before you first release them.

## Versioning events

Sooner or later an event's payload needs to change shape. How you handle it
depends on whether the change is additive or breaking.

### Additive changes

Adding a new field to an existing event is usually safe if you make it
nullable and give it a sensible fallback when applying old events. Jackson
deserializes missing JSON properties as `null` for reference types, so
existing history that predates the field will produce `null` on replay:

```java
@DomainEvent("vehicle-registered")
public record VehicleRegistered(String licensePlate, String ownerId, String color) {
}
```

```java
@DomainEventHandler
void apply(VehicleRegistered evt) {
    this.licensePlate = evt.licensePlate();
    this.ownerId = evt.ownerId();
    this.color = evt.color() != null ? evt.color() : "unknown";
}
```

This works because the record's class and logical name don't change —
old and new events both deserialize to the same `VehicleRegistered` type,
just with `color` populated only on newer ones.

### Breaking changes

If a change is genuinely breaking — removing a field, changing its type or
meaning, restructuring the payload — don't repurpose the existing event
class. Introduce a new event type with its own logical name, and keep the
old one around purely to support replaying old history:

```java
@DomainEvent("vehicle-registered")
public record VehicleRegistered(String licensePlate, String ownerId) {
}

@DomainEvent("vehicle-registered-v2")
public record VehicleRegisteredV2(String licensePlate, String ownerId, String color) {
}
```

On the aggregate, keep the handler for the old event so historic streams
still replay correctly, and add a new handler for the new one:

```java
@DomainEventHandler
void apply(VehicleRegistered evt) {
    this.licensePlate = evt.licensePlate();
    this.ownerId = evt.ownerId();
    this.color = "unknown";
}

@DomainEventHandler
void apply(VehicleRegisteredV2 evt) {
    this.licensePlate = evt.licensePlate();
    this.ownerId = evt.ownerId();
    this.color = evt.color();
}
```

This works because `@DomainEventHandler` methods are matched by their exact
parameter type — an aggregate can happily have one handler per version of
an event, side by side. Update the command-handling method to emit only the
new version going forward:

```java
public static Vehicle register(RegisterVehicle cmd) {
    Vehicle vehicle = new Vehicle();
    var event = new VehicleRegisteredV2(cmd.licensePlate(), cmd.ownerId(), cmd.color());
    AggregateLifecycle.instance().emitDomainEvent(vehicle, event);
    return vehicle;
}
```

Once you're confident no aggregate still needs to replay the old event (or
you've migrated old history), you can remove the old class and its handler.

## Renaming an aggregate

The same rule applies here as with events: **renaming the aggregate's Java
class is safe**, since the class name is never stored — only the
`@EventStream` value is, in `aggregate_type`. Rename `Vehicle` to
`RegisteredVehicle` freely as long as `@EventStream("vehicle")` stays the
same.

**Changing the `@EventStream` value is not safe** for aggregates that
already have events persisted. `AggregateRepository.load` filters the event
store by the current `@EventStream` value, so renaming it makes every
existing aggregate instance look like it no longer exists —
`AggregateRepository.load` throws `AggregateNotFoundException` for ids that
are actually still in the database, just under the old type name.

`@EventStream` values must also be globally unique across all aggregates —
the build fails with `Duplicate event stream name` if two aggregate classes
declare the same value.

If you must rename it anyway, migrate the stored value first:

```sql
UPDATE event_streams
SET aggregate_type = 'registered-vehicle'
WHERE aggregate_type = 'vehicle';
```

Then update `@EventStream` in code to match, in the same deployment as the
migration, so there's no window where the two are out of sync.

## The general rule

`@DomainEvent` and `@EventStream` logical names are part of your
application's persisted, public contract, in the same way a database column
name is. Choose them deliberately before release. Java class and method
names, on the other hand, are free to refactor at any time — Lexicon never
looks at them.
