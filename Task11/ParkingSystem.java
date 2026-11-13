import java.util.*;

interface Chargeable {
    double chargingFee(int kWh);
}

abstract class Vehicle {
    private final String regNo;

    protected Vehicle(String regNo) {
        if (regNo == null || regNo.isBlank())
            throw new IllegalArgumentException("Registration number required");
        this.regNo = regNo;
    }

    public String getRegNo() { return regNo; }

    protected abstract double ratePerHour();

    /** Every started hour is charged in full. 0 minutes = Rs 0. */
    public double calculateFee(int minutes) {
        if (minutes < 0) throw new IllegalArgumentException("Negative minutes");
        int hours = (minutes + 59) / 60;
        return hours * ratePerHour();
    }

    @Override public boolean equals(Object o) {
        return o instanceof Vehicle && regNo.equals(((Vehicle) o).regNo);
    }

    @Override public int hashCode() { return regNo.hashCode(); }
}

class TwoWheeler extends Vehicle {
    TwoWheeler(String regNo) { super(regNo); }
    @Override protected double ratePerHour() { return 10; }
}

class Car extends Vehicle {
    Car(String regNo) { super(regNo); }
    @Override protected double ratePerHour() { return 30; }
}

class ElectricCar extends Car implements Chargeable {
    ElectricCar(String regNo) { super(regNo); }
    @Override public double chargingFee(int kWh) {
        if (kWh < 0) throw new IllegalArgumentException("Negative kWh");
        return kWh * 12;
    }
}

class ParkingTicket {
    private static int counter = 0;          // shared by all tickets, never reused
    private final int ticketNo;
    private final Vehicle vehicle;
    private final int floor, slot, entryTime;

    ParkingTicket(Vehicle vehicle, int floor, int slot, int entryTime) {
        this.ticketNo = ++counter;
        this.vehicle = vehicle;
        this.floor = floor;
        this.slot = slot;
        this.entryTime = entryTime;
    }

    public int getTicketNo() { return ticketNo; }
    public Vehicle getVehicle() { return vehicle; }
    public int getFloor() { return floor; }
    public int getSlot() { return slot; }
    public int getEntryTime() { return entryTime; }
}

class ParkingFacility {
    private final ParkingTicket[][] slots;                       // jagged array
    private final Map<String, ParkingTicket> active = new HashMap<>(); // regNo -> ticket

    public ParkingFacility(int[] slotsPerFloor) {
        slots = new ParkingTicket[slotsPerFloor.length][];
        for (int f = 0; f < slotsPerFloor.length; f++)
            slots[f] = new ParkingTicket[slotsPerFloor[f]];
    }

    static Vehicle createVehicle(String type, String regNo) {
        switch (type.toUpperCase()) {
            case "TWO_WHEELER": return new TwoWheeler(regNo);
            case "CAR":         return new Car(regNo);
            case "EV":          return new ElectricCar(regNo);
            default: throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }

    /** Returns the ticket, or null if the facility is full. */
    public ParkingTicket enter(String type, String regNo, int entryTime) {
        Vehicle v = createVehicle(type, regNo);
        if (active.containsKey(regNo))
            throw new IllegalStateException("Duplicate registration: " + regNo);
        for (int f = 0; f < slots.length; f++) {
            for (int s = 0; s < slots[f].length; s++) {
                if (slots[f][s] == null) {
                    ParkingTicket t = new ParkingTicket(v, f, s, entryTime);
                    slots[f][s] = t;
                    active.put(regNo, t);
                    return t;
                }
            }
        }
        return null;
    }

    /** Releases the slot and returns the fee. Pass holders park free; charging is extra. */
    public double exit(String regNo, int exitTime, boolean hasPass, int kWh) {
        ParkingTicket t = active.get(regNo);
        if (t == null) throw new IllegalStateException("No active ticket for " + regNo);
        if (exitTime < t.getEntryTime()) throw new IllegalArgumentException("Exit before entry");
        slots[t.getFloor()][t.getSlot()] = null;
        active.remove(regNo);
        double fee = hasPass ? 0 : t.getVehicle().calculateFee(exitTime - t.getEntryTime());
        if (t.getVehicle() instanceof Chargeable)
            fee += ((Chargeable) t.getVehicle()).chargingFee(kWh);
        return fee;
    }

    public boolean release(int floor, int slot) {
        if (floor < 0 || floor >= slots.length) return false;
        if (slot < 0 || slot >= slots[floor].length) return false;
        if (slots[floor][slot] == null) return false;
        active.remove(slots[floor][slot].getVehicle().getRegNo());
        slots[floor][slot] = null;
        return true;
    }
}

public class ParkingSystem {
    static int passed = 0, failed = 0;

    static void check(String name, String expected, String actual) {
        boolean ok = expected.equals(actual);
        if (ok) passed++; else failed++;
        System.out.printf("%-38s | %-10s | %-10s | %s%n", name, expected, actual,
                ok ? "PASS" : "FAIL");
    }

    static String slotOf(ParkingTicket t) {
        return t == null ? "null" : "[" + t.getFloor() + ", " + t.getSlot() + "]";
    }

    public static void main(String[] args) {
        System.out.printf("%-38s | %-10s | %-10s | %s%n", "Test", "Expected", "Actual", "Result");
        System.out.println("-".repeat(75));

        // floor 0 has 2 slots, floor 1 has 1 slot
        ParkingFacility p = new ParkingFacility(new int[]{2, 1});

        // ---- Normal operation ----
        check("Two-wheeler enters", "[0, 0]", slotOf(p.enter("TWO_WHEELER", "TN01", 0)));
        check("Car enters", "[0, 1]", slotOf(p.enter("CAR", "TN02", 0)));

        // ---- Boundary: last free slot, then full ----
        check("EV takes last free slot", "[1, 0]", slotOf(p.enter("EV", "TN03", 10)));
        check("Facility full", "null", slotOf(p.enter("CAR", "TN04", 0)));

        // ---- Invalid cases ----
        String r;
        try { p.enter("CAR", "TN01", 5); r = "accepted"; } catch (IllegalStateException e) { r = "rejected"; }
        check("Duplicate registration", "rejected", r);
        try { p.enter("TRUCK", "TN09", 5); r = "accepted"; } catch (IllegalArgumentException e) { r = "rejected"; }
        check("Unknown vehicle type", "rejected", r);
        check("Invalid slot number release(5,0)", "false", String.valueOf(p.release(5, 0)));

        // ---- Boundary: zero minutes and slab boundary ----
        check("Zero-minute stay (TN01)", "0.0", String.valueOf(p.exit("TN01", 0, false, 0)));
        check("Exactly 60 min car (TN02)", "30.0", String.valueOf(p.exit("TN02", 60, false, 0)));
        p.enter("CAR", "TN05", 0);
        check("61 min car (next hour started)", "60.0", String.valueOf(p.exit("TN05", 61, false, 0)));
        check("EV 60 min + 5 kWh (TN03)", "90.0", String.valueOf(p.exit("TN03", 70, false, 5)));

        // ---- Hand-calculated scenario ----
        ParkingFacility day = new ParkingFacility(new int[]{2, 1});
        day.enter("TWO_WHEELER", "TN11", 0);
        day.enter("CAR", "TN12", 0);
        day.enter("EV", "TN13", 10);
        double total = 0;
        total += day.exit("TN11", 45, false, 0);   // 1 hour x 10  = 10
        total += day.exit("TN12", 130, false, 0);  // 3 hours x 30 = 90
        total += day.exit("TN13", 70, false, 5);   // 1 hour x 30 + 5 kWh x 12 = 90
        day.enter("CAR", "TN14", 0);
        total += day.exit("TN14", 200, true, 0);   // monthly pass = 0
        check("Day total (hand-calculated 190.0)", "190.0", String.valueOf(total));

        System.out.println("-".repeat(75));
        System.out.println("Passed: " + passed + "  Failed: " + failed);
    }
}
