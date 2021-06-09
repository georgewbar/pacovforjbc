package pacovfor$jbc.analysis.testrequirements;

import pacovfor$jbc.graph.Node;

import java.util.Objects;

public class NodeTR<NT> extends AbstractTestRequirement {

    private final Node<NT> node;

    public NodeTR(Node<NT> node) {
        this.node = node;
    }

    public Node<NT> getNode() {
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeTR<?> that = (NodeTR<?>) o;
        return Objects.equals(this.node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node);
    }
}
