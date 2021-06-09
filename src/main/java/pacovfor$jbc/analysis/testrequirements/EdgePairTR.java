package pacovfor$jbc.analysis.testrequirements;

import pacovfor$jbc.graph.Edge;

import java.util.Objects;

/**
 * Edge-pair test requirement. Two edge-pair test requirements are considered equal
 * if the edge-pairs stored in them are equal regardless of "covered" status of the
 * edge-pairs.
 *
 * An edge-pair should be considered covered if both edges in the edge-pair are covered.
 */
public class EdgePairTR<NT> extends AbstractTestRequirement {
    private final Edge<NT, Object> _1;
    private final Edge<NT, Object> _2;

    public EdgePairTR(Edge<NT, Object> _1, Edge<NT, Object> _2) {
        super();

        if (_1 == null || _2 == null) {
            throw new IllegalArgumentException("_1 or _2 is null");
        }

        this._1 = _1;
        this._2 = _2;
    }

    public Edge<NT, Object> _1() {
        return _1;
    }

    public Edge<NT, Object> _2() {
        return _2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdgePairTR<?> that = (EdgePairTR<?>) o;
        return Objects.equals(this._1, that._1) && Objects.equals(this._2, that._2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_1, _2);
    }

    @Override
    public String toString() {
        return "EdgePair(" + _1 + ", " + _2 + "): " + isCovered();
    }
}
