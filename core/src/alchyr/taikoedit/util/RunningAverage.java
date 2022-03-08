package alchyr.taikoedit.util;

public class RunningAverage {
    private final double[] slots;
    private int offset;

    public RunningAverage(int slotCount) {
        this.slots = new double[slotCount];
        this.offset = 0;
    }

    public void init(double value) {
        while (this.offset < this.slots.length) {
            this.slots[this.offset++] = value;
        }
    }

    public void add(double value) {
        this.slots[this.offset++ % this.slots.length] = value;
        this.offset %= this.slots.length;
    }

    public double avg() {
        double sum = 0;
        for (double slot : this.slots) {
            sum = sum + slot;
        }
        return sum / this.slots.length;
    }
}