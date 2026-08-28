package nl.beyondautocomplete.lexicon.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.beyondautocomplete.lexicon.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class VehicleAggregateTest {

    @DomainEvent("it.vehicle-registered")
    public record VehicleRegistered(String licensePlate, String ownerId) {
    }

    @DomainEvent("it.vehicle-owner-changed")
    public record OwnerChanged(String ownerId) {
    }

    public record RegisterVehicle(String licensePlate, String ownerId) {
    }

    public record ChangeOwner(String ownerId) {
    }

    @EventStream("it.vehicle")
    public static class Vehicle extends AggregateRoot {
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
            VehicleRegistered vehicleRegistered = new VehicleRegistered(cmd.licensePlate(), cmd.ownerId());
            AggregateLifecycle.instance().emitDomainEvent(vehicle, vehicleRegistered);

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

    @Inject
    VehicleRepository vehicleRepository;

    private static String randomLicensePlate() {
        return "AA-" + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void savesAndLoadsANewlyCreatedAggregate() {
        var licensePlate = randomLicensePlate();
        var vehicle = Vehicle.register(new RegisterVehicle(licensePlate, "owner-1"));

        vehicleRepository.save(vehicle);

        var loaded = vehicleRepository.load(licensePlate);

        assertEquals(licensePlate, loaded.getLicensePlate());
        assertEquals("owner-1", loaded.getOwnerId());
        assertEquals(1, loaded.version());
    }

    @Test
    void appliesSubsequentChangesOnTopOfReplayedHistory() {
        var licensePlate = randomLicensePlate();
        var vehicle = Vehicle.register(new RegisterVehicle(licensePlate, "owner-1"));
        vehicleRepository.save(vehicle);

        var loaded = vehicleRepository.load(licensePlate);
        loaded.changeOwner(new ChangeOwner("owner-2"));
        vehicleRepository.save(loaded);

        var reloaded = vehicleRepository.load(licensePlate);

        assertEquals("owner-2", reloaded.getOwnerId());
        assertEquals(2, reloaded.version());
    }

    @Test
    void loadThrowsWhenTheAggregateDoesNotExist() {
        assertThrows(AggregateNotFoundException.class, () -> vehicleRepository.load("unknown-plate"));
    }

    @Test
    void saveThrowsOptimisticConcurrencyExceptionWhenTwoLoadedCopiesDiverge() {
        var licensePlate = randomLicensePlate();
        var vehicle = Vehicle.register(new RegisterVehicle(licensePlate, "owner-1"));
        vehicleRepository.save(vehicle);

        var first = vehicleRepository.load(licensePlate);
        var second = vehicleRepository.load(licensePlate);

        first.changeOwner(new ChangeOwner("owner-2"));
        vehicleRepository.save(first);

        second.changeOwner(new ChangeOwner("owner-3"));
        assertThrows(OptimisticConcurrencyException.class, () -> vehicleRepository.save(second));
    }
}
