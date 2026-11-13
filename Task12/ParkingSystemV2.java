import java.util.*;

/* ===================== UNCHANGED classes from Task 11 ===================== */

interface Chargeable {
    double chargingFee(int kWh);
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
    private static int counter = 0;
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

/* ===================== MODIFIED class 1: Vehicle (one method added) ===================== */

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

    /** NEW: time-aware fee. Default ignores the entry time; subclasses may override. */
    public double calculateFee(int entryTime, int minutes) {
        return calculateFee(minutes);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Vehicle && regNo.equals(((Vehicle) o).regNo);
    }

    @Override public int hashCode() { return regNo.hashCode(); }
}

/* ===================== NEW classes (existing code only extended) ===================== */

/** E-bike: flat fee per visit, whatever the duration. */
class EBike extends Vehicle {
    static final double FLAT_FEE = 20;

    EBike(String regNo) { super(regNo); }

    @Override protected double ratePerHour() { return 0; }   // not used: fee is flat

    @Override public double calculateFee(int minutes) {
        if (minutes < 0) throw new IllegalArgumentException("Negative minutes");
        return FLAT_FEE;
    }
}

/** Delivery van: double the car rate for every billable hour that starts between 9 AM and 5 PM. */
class DeliveryVan extends Car {
    DeliveryVan(String regNo) { super(regNo); }

    @Override public double calculateFee(int entryTime, int minutes) {
        if (minutes < 0) throw new IllegalArgumentException("Negative minutes");
        int hours = (minutes + 59) / 60;
        double fee = 0;
        for (int i = 0; i < hours; i++) {
            int start = (entryTime + 60 * i) % 1440;          // minute of the day
            boolean peak = start >= 9 * 60 && start < 17 * 60;  // 9:00 <= start < 17:00
            fee += peak ? 2 * ratePerHour() : ratePerHour();
        }
        return fee;
    }
}

/** A pass decides whether a stay is free of parking charge. */
interface Pass {
    boolean covers(int entryTime, int exitTime);
}

/** Monthly pass for students and staff: valid from day 'fromDay' to day 'toDay' (day = minutes / 1440). */
class MonthlyPass implements Pass {
    private final int fromDay, toDay;
    MonthlyPass(int fromDay, int toDay) { this.fromDay = fromDay; this.toDay = toDay; }
    @Override public boolean covers(int entryTime, int exitTime) {
        return entryTime / 1440 >= fromDay && exitTime / 1440 <= toDay;
    }
}

/** One-day pass for visitors: valid only on one day. */
class DayPass implements Pass {
    private final int day;
    DayPass(int day) { this.day = day; }
    @Override public boolean covers(int entryTime, int exitTime) {
        return entryTime / 1440 == day && exitTime / 1440 == day;
    }
}

/* ===================== MODIFIED class 2: ParkingFacility ===================== */

class ParkingFacility {
    private final ParkingTicket[][] slots;                              // jagged array: any slots per floor
    private final Map<String, ParkingTicket> active = new HashMap<>();

    public ParkingFacility(int[] slotsPerFloor) {
        slots = new ParkingTicket[slotsPerFloor.length][];
        for (int f = 0; f < slotsPerFloor.length; f++)
            slots[f] = new ParkingTicket[slotsPerFloor[f]];
    }

    static Vehicle createVehicle(String type, String regNo) {
        switch (type.toUpperCase()) {
            case "TWO_WHEELER":   return new TwoWheeler(regNo);
            case "CAR":           return new Car(regNo);
            case "EV":            return new ElectricCar(regNo);
            case "E_BIKE":        return new EBike(regNo);          // new
            case "DELIVERY_VAN":  return new DeliveryVan(regNo);    // new
            default: throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }

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

    /** pass may be null (no pass). Charging is always extra. */
    public double exit(String regNo, int exitTime, Pass pass, int kWh) {
        ParkingTicket t = active.get(regNo);
        if (t == null) throw new IllegalStateException("No active ticket for " + regNo);
        if (exitTime < t.getEntryTime()) throw new IllegalArgumentException("Exit before entry");
        slots[t.getFloor()][t.getSlot()] = null;
        active.remove(regNo);
        boolean free = pass != null && pass.covers(t.getEntryTime(), exitTime);
        double fee = free ? 0 : t.getVehicle().calculateFee(t.getEntryTime(), exitTime - t.getEntryTime());
        if (t.getVehicle() instanceof Chargeable)
            fee += ((Chargeable) t.getVehicle()).chargingFee(kWh);
        return fee;
    }
}

/* ===================== Tests ===================== */

public class ParkingSystemV2 {
    static int passed = 0, failed = 0;

    static void check(String name, String expected, String actual) {
        boolean ok = expected.equals(actual);
        if (ok) passed++; else failed++;
        System.out.printf("%-40s | %-8s | %-8s | %s%n", name, expected, actual, ok ? "PASS" : "FAIL");
    }

    static String slotOf(ParkingTicket t) {
        return t == null ? "null" : "[" + t.getFloor() + ", " + t.getSlot() + "]";
    }

    /** Enter at entry, exit at exit, return the fee. */
    static String fee(ParkingFacility f, String type, String reg, int in, int out, Pass pass, int kWh) {
        f.enter(type, reg, in);
        return String.valueOf(f.exit(reg, out, pass, kWh));
    }

    public static void main(String[] args) {
        System.out.printf("%-40s | %-8s | %-8s | %s%n", "Test", "Expected", "Actual", "Result");
        System.out.println("-".repeat(70));

        // 5 floors, different number of slots on each floor
        ParkingFacility f = new ParkingFacility(new int[]{2, 3, 1, 4, 2});

        // ---- New category 1: e-bike flat fee ----
        check("E-bike 30 min (flat Rs 20)", "20.0", fee(f, "E_BIKE", "EB1", 0, 30, null, 0));
        check("E-bike 300 min (flat Rs 20)", "20.0", fee(f, "E_BIKE", "EB2", 0, 300, null, 0));

        // ---- New category 2: delivery van, double rate 9 AM - 5 PM ----
        check("Van 10:00, 120 min (all peak)", "120.0", fee(f, "DELIVERY_VAN", "VAN1", 600, 720, null, 0));
        check("Van 20:00, 120 min (all off-peak)", "60.0", fee(f, "DELIVERY_VAN", "VAN2", 1200, 1320, null, 0));
        check("Van 16:30, 90 min (crosses 5 PM)", "90.0", fee(f, "DELIVERY_VAN", "VAN3", 990, 1080, null, 0));
        check("Van starts exactly 17:00, 60 min", "30.0", fee(f, "DELIVERY_VAN", "VAN4", 1020, 1080, null, 0));
        check("Van starts exactly 9:00, 60 min", "60.0", fee(f, "DELIVERY_VAN", "VAN5", 540, 600, null, 0));

        // ---- New: one-day visitor pass and monthly pass ----
        check("Visitor car, valid one-day pass", "0.0", fee(f, "CAR", "P1", 600, 900, new DayPass(0), 0));
        check("Visitor car, pass for another day", "60.0", fee(f, "CAR", "P2", 600, 720, new DayPass(1), 0));
        check("Student car, monthly pass", "0.0", fee(f, "CAR", "P3", 0, 200, new MonthlyPass(0, 29), 0));

        // ---- Old categories still give the same results as Task 8 ----
        check("Two-wheeler 130 min", "30.0", fee(f, "TWO_WHEELER", "O1", 0, 130, null, 0));
        check("Car 130 min", "90.0", fee(f, "CAR", "O2", 0, 130, null, 0));
        check("EV 130 min + 10 kWh", "210.0", fee(f, "EV", "O3", 0, 130, null, 10));

        // ---- 5 floors with different slot counts (2,3,1,4,2 = 12 slots) ----
        ParkingFacility g = new ParkingFacility(new int[]{2, 3, 1, 4, 2});
        String sixth = "", twelfth = "";
        for (int i = 0; i < 12; i++) {
            String s = slotOf(g.enter("CAR", "J" + i, 0));
            if (i == 5) sixth = s;
            if (i == 11) twelfth = s;
        }
        check("6th car goes to 1-slot floor 2", "[2, 0]", sixth);
        check("12th car takes last slot", "[4, 1]", twelfth);
        check("13th car, facility full", "null", slotOf(g.enter("CAR", "J12", 0)));

        // ---- Invalid cases still rejected ----
        String r;
        try { f.enter("TRUCK", "X1", 0); r = "accepted"; } catch (IllegalArgumentException e) { r = "rejected"; }
        check("Unknown vehicle type", "rejected", r);
        f.enter("CAR", "DUP", 0);
        try { f.enter("CAR", "DUP", 5); r = "accepted"; } catch (IllegalStateException e) { r = "rejected"; }
        check("Duplicate registration", "rejected", r);

        // ---- Hand-calculated scenario ----
        ParkingFacility day = new ParkingFacility(new int[]{2, 3, 1, 4, 2});
        day.enter("E_BIKE", "S1", 540);
        day.enter("DELIVERY_VAN", "S2", 600);
        day.enter("DELIVERY_VAN", "S3", 990);
        day.enter("CAR", "S4", 600);
        day.enter("CAR", "S5", 0);
        double total = 0;
        total += day.exit("S1", 840, null, 0);              // flat 20
        total += day.exit("S2", 720, null, 0);              // 2 peak hours x 60 = 120
        total += day.exit("S3", 1080, null, 0);             // 60 + 30 = 90
        total += day.exit("S4", 1000, new DayPass(0), 0);   // one-day pass = 0
        total += day.exit("S5", 130, null, 0);              // 3 hours x 30 = 90
        check("Day total (hand-calculated 320.0)", "320.0", String.valueOf(total));

        System.out.println("-".repeat(70));
        System.out.println("Passed: " + passed + "  Failed: " + failed);
    }
}
