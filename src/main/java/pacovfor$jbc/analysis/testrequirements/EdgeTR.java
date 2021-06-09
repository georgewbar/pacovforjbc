package pacovfor$jbc.analysis.testrequirements;

import pacovfor$jbc.graph.Edge;

import java.util.Objects;

/**
 * Edge test requirement. Two edge test requirements are considered equal
 * if the edges stored in them are equal regardless of their "covered" status.
 */
public class EdgeTR<NT> extends AbstractTestRequirement {
    private final Edge<NT, Object> edge;

    public EdgeTR(Edge<NT, Object> edge) {
        super();
        if (edge == null) {
            throw new IllegalArgumentException("edge is null");
        }

        this.edge = edge;
    }

    public Edge<NT, Object> edge() {
        return edge;
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdgeTR<?> that = (EdgeTR<?>) o;
        return Objects.equals(this.edge, that.edge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edge);
    }
}
