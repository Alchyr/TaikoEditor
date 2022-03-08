package alchyr.taikoedit.util.structures;

import java.util.Map;
import java.util.Objects;

public class Pair<T, U> implements Map.Entry<T, U> {
    public T a;
    public U b;

    public Pair(T a, U b)
    {
        this.a = a;
        this.b = b;
    }

    @Override
    public T getKey() {
        return a;
    }

    @Override
    public U getValue() {
        return b;
    }

    @Override
    public U setValue(U value) {
        return b = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(a, pair.a) && Objects.equals(b, pair.b);
    }

    @Override
    public int hashCode() {
        return (getKey()==null ? 0 : getKey().hashCode()) ^
             (getValue()==null ? 0 : getValue().hashCode());
    }
}
