package pacovfor$jbc.analysis.graphtypes;

import pacovfor$jbc.analysis.FlowType;
import pacovfor$jbc.backend.asmadapters.MethodAdapter;
import pacovfor$jbc.analysis.nodetypes.ProbePosition;
import pacovfor$jbc.utils.Utils;
import pacovfor$jbc.graph.Edge;
import pacovfor$jbc.graph.Graph;
import pacovfor$jbc.graph.Node;
import pacovfor$jbc.analysis.nodetypes.ProbePositionID;
import org.apache.commons.text.StringEscapeUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProbePositionCfg extends Graph<ProbePosition, FlowType> {

    public ProbePositionCfg() {
        super();
    }

    private String getDotNodeID(ProbePosition pb) {
        String isEntry = pb.isEntry() ? " isEntry" : "";
        String isExit = pb.isExit() ? " isExit" : "";

        return String.format("pd%d_inst_%d%s%s", pb.getId(), pb.getInstruction().index(), isEntry, isExit);
    }

    public ProbePositionIDCfg toProbePositionIDCfg(MethodAdapter methodAdapter) {
        String filePath = Utils.getRelativeFilePathOfMethod(methodAdapter);
        String fullMethodName = Utils.getFullMethodName(methodAdapter);

        ProbePositionIDCfg newCfg = new ProbePositionIDCfg(filePath, fullMethodName);

        // set root
        newCfg.setRoot(new Node<>(new ProbePositionID(this.getRoot().getData().getId())));

        // add all node to the probePositionIDCfg
        for (Node<ProbePosition> pb : this.getAllNodes()) {
            newCfg.addNode(new Node<>(new ProbePositionID(pb.getData().getId())));
        }

        // add all edges to the probePositionIDCfg
        for (Edge<ProbePosition, FlowType> edge : this.getAllEdges()) {
            newCfg.addEdge(
                    new Node<>(new ProbePositionID(edge.getSource().getData().getId())),
                    new Node<>(new ProbePositionID(edge.getDestination().getData().getId())),
                    edge.getData()
            );
        }

        return newCfg;
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean printInstructions) {
        OutputStream baOut = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baOut);

        out.println("digraph model {");
        out.println("  graph [ rankdir = \"TB\", ranksep=\"0.4\", nodesep=\"0.2\" ];");
        out.println("  node [ fontname = \"Helvetica\", fontsize=\"12.0\"," + " margin=\"0.07\" ];");
        out.println("  edge [ fontname = \"Helvetica\", fontsize=\"12.0\"," + " margin=\"0.05\" ];");
        // initial state
        out.println("  \"\" [ shape = \"point\", height=\"0.1\" ];");

        // print nodes
        out.println("  {");
        out.println("    node [ shape = \"box\"]");
        Set<Node<ProbePosition>> nodes = this.getAllNodes();
        for (Node<ProbePosition> pbNode : nodes) {
            ProbePosition pb = pbNode.getData();

            String isEntry = pb.isEntry() ? " isEntry" : "";
            String isExit = pb.isExit() ? " isExit" : "";

            if (!printInstructions) {
                out.printf("    \"%s\";%n", getDotNodeID(pb));
            } else {
                String label = String.format("pd%d_%d: %s%n%s%s", pb.getId(), pb.getInstruction().index(),
                        pb.getInstruction(), isEntry, isExit);

                out.printf("    \"%s\" [label=<%s>];%n", getDotNodeID(pb),
                        StringEscapeUtils.escapeHtml3(label).replace(System.lineSeparator(), "<br/>").
                                replace("\n", "<br/>"));
            }
        }

        out.println("  }");

        // print clusters
        Map<Integer, List<ProbePosition>> basicBlockIdToProbes = nodes.stream().
                map(Node::getData).collect(Collectors.groupingBy(ProbePosition::basicBlockIndex));

        basicBlockIdToProbes.forEach((basicBlockID, probePositions) -> {
            out.printf("  subgraph cluster_%d {%n", basicBlockID);
            out.printf("    label = \"bb_%d\"%n", basicBlockID);
            probePositions.forEach(pb -> out.printf("    \"%s\";%n", getDotNodeID(pb)));
            out.println("  }");
        });

        // print an edge to the root node
        Node<ProbePosition> rootNode = this.getRoot();
        out.printf("  \"\" -> \"%s\";%n", getDotNodeID(rootNode.getData()));

        // create a discovered set of nodes and add the root to it
        Set<Node<ProbePosition>> discoveredNodes = new HashSet<>();
        discoveredNodes.add(rootNode);

        // visit all edges.
        for (Edge<ProbePosition, FlowType> edge : this.getAllEdges()) {
            Node<ProbePosition> src = edge.getSource();
            Node<ProbePosition> dest = edge.getDestination();

            if (edge.getData() == FlowType.NORMAL_FLOW) {
                out.printf("  \"%s\" -> \"%s\";%n", getDotNodeID(src.getData()), getDotNodeID(dest.getData()));
            } else if (edge.getData() == FlowType.EXCEPTIONAL_FLOW) {
                out.printf("  \"%s\" -> \"%s\" [color=\"red\"];%n",
                        getDotNodeID(src.getData()), getDotNodeID(dest.getData()));
            } else {
                throw new IllegalStateException("flow edge data is null");
            }

            // add source node and destination node to discovered set
            discoveredNodes.add(src);
            discoveredNodes.add(dest);
        }

        // log all undiscovered nodes
        for (Node<ProbePosition> node : this.getAllNodes()) {
            if (!discoveredNodes.contains(node)) {
                // undiscovered nodes were already printed in dot format under "nodes" part
                System.err.println("ProbePositionCfg [INFO] node: " + node.getData().getId() + " has neither outgoing nor " +
                        "ingoing edges.");
            }
        }

        // print a rectangle around basic blocks
        out.println();
        out.println("}");
        out.flush();
        out.close();

        return baOut.toString();
    }

}
