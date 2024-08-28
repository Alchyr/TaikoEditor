package alchyr.taikoedit.util.interfaces;

import java.util.function.Supplier;

public class KnownAmountSupplier<T> {
    private final int amount;
    private final Supplier<T> supplier;

    public KnownAmountSupplier(int amt, Supplier<T> supplier) {
        this.amount = amt;
        this.supplier = supplier;
    }

    public int getAmount() {
        return amount;
    }

    public T get() {
        return supplier.get();
    }
}
