package pacovfor$jbc.analysis.nodetypes;

import java.util.Objects;

public class ProbePositionID {

    private final int id;

    public ProbePositionID(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProbePositionID that = (ProbePositionID) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "" + this.id;
    }
}
