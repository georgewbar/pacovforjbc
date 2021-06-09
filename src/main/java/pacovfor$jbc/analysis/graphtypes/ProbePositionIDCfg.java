package pacovfor$jbc.analysis.graphtypes;

import pacovfor$jbc.analysis.FlowType;
import pacovfor$jbc.analysis.nodetypes.ProbePositionID;
import pacovfor$jbc.analysis.testrequirements.NodeTR;
import pacovfor$jbc.config.Config;
import pacovfor$jbc.graph.Edge;
import pacovfor$jbc.graph.Graph;
import pacovfor$jbc.graph.Node;
import pacovfor$jbc.analysis.testrequirements.AbstractTestRequirement;
import pacovfor$jbc.analysis.testrequirements.EdgePairTR;
import pacovfor$jbc.analysis.testrequirements.EdgeTR;
import pacovfor$jbc.utils.Tuple;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static pacovfor$jbc.analysis.FlowType.NORMAL_FLOW;
import static pacovfor$jbc.analysis.FlowType.EXCEPTIONAL_FLOW;

public class ProbePositionIDCfg extends Graph<ProbePositionID, FlowType> {

    private final String filePath;

    public final static String NODES_COVERED = "NODES_COVERED";
    public final static String TOTAL_NODES = "TOTAL_NODES";

    public final static String EDGES_COVERED = "EDGES_COVERED";
    public final static String TOTAL_EDGES = "TOTAL_EDGES";

    public final static String EDGE_PAIRS_COVERED = "EDGE_PAIRS_COVERED";
    public final static String TOTAL_EDGE_PAIRS = "TOTAL_EDGE_PAIRS";

    private final Map<NodeTR<ProbePositionID>, NodeTR<ProbePositionID>> nodeReqs = new HashMap<>();
    private final Map<EdgeTR<ProbePositionID>, EdgeTR<ProbePositionID>> edgesReqs = new HashMap<>();
    private final Map<EdgePairTR<ProbePositionID>, EdgePairTR<ProbePositionID>> edgePairsReqs = new HashMap<>();
    private final String fullMethodName;

    /**
     * a field indicating whether the method represented by the cfg was entered.
     * Note: volatile; should not affect the probes as it will read last.
     */
    private volatile boolean isEntered;

    public ProbePositionIDCfg(String filePath, String fullMethodName) {
        this.fullMethodName = fullMethodName;
        this.filePath = filePath;
        this.isEntered = false;
    }

    public void setEntered(boolean isEntered) {
        this.isEntered = isEntered;
    }

    public boolean isEntered() {
        return isEntered;
    }

    /**
     * Print cfg to a file with a pre-defined format that can be read by the
     * "read" factory function.
     */
    public void printToFile() {
        String targetDir = Config.cfgsDir;
        File dirFile = new File( targetDir + File.separator +
                filePath.substring(0, filePath.indexOf(File.separator)));

        if (!dirFile.exists() && !dirFile.mkdirs()) {
            throw new IllegalArgumentException("dir: " + dirFile + " is not correct");
        }

        PrintStream out;
        try {
            out = new PrintStream(targetDir + File.separator + filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // print file path
        out.println(filePath);

        // print full method name
        out.println(fullMethodName);

        // print isEntered
        out.println(isEntered());

        // print number of nodes followed by all node ids
        Set<Node<ProbePositionID>> nodes = this.getAllNodes();
        out.println(nodes.size());
        for (Node<ProbePositionID> node : nodes) {
            out.println(node.getData().getId());
        }

        // print number of edges followed by all edges (src-node-id, dest-node-id (normal|exceptional))
        List<Edge<ProbePositionID, FlowType>> edges = this.getAllEdges();
        out.println(edges.size());
        for (Edge<ProbePositionID, FlowType> edge : edges) {
            out.printf("%d %d %s%n", edge.getSource().getData().getId(), edge.getDestination().getData().getId(),
                    edge.getData() == NORMAL_FLOW ? "normal" : "exceptional");
        }

        out.flush();
        out.close();
    }

    public static ProbePositionIDCfg readCfgFromFile(String fileName) {
        return readCfgFromFile(fileName, false);
    }

    public static ProbePositionIDCfg readCfgFromFile(String fileName, boolean isAbsolutePath) {
        BufferedReader br;
        try {
            if (isAbsolutePath) {
                br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8));
            } else {
                br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(Config.cfgsDir + File.separator + fileName),
                                StandardCharsets.UTF_8));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        ProbePositionIDCfg probePositionIDCfg = null;
        Throwable catchExc = null;
        Throwable finallyExc = null;
        try {
            String relativeFilePathOfMethod = br.readLine(); // relative file path of method
            String fullMethodName = br.readLine(); // full method name

            probePositionIDCfg = new ProbePositionIDCfg(relativeFilePathOfMethod, fullMethodName);

            br.readLine(); // read isEntered and ignore

            // parse nodes
            int numberOfNodes = Integer.parseInt(br.readLine());
            for (int i = 0; i < numberOfNodes; i++) {
                String node = br.readLine();
                int probePositionId = Integer.parseInt(node);
                probePositionIDCfg.addNode(new Node<>(new ProbePositionID(probePositionId)));
            }

            // parse edges
            int numberOfEdges = Integer.parseInt(br.readLine());
            for (int i = 0; i < numberOfEdges; i++) {
                String edge = br.readLine();
                String[] srcDestType = edge.split(" ");
                int src = Integer.parseInt(srcDestType[0]);
                int dest = Integer.parseInt(srcDestType[1]);
                FlowType flowType = srcDestType[2].equals("normal") ?
                        NORMAL_FLOW : EXCEPTIONAL_FLOW;

                Node<ProbePositionID> srcNode = new Node<>(new ProbePositionID(src));
                Node<ProbePositionID> destNode = new Node<>(new ProbePositionID(dest));

                probePositionIDCfg.addEdge(srcNode, destNode, flowType);
            }

            // sanity check
            String currentLine = br.readLine();
            if (currentLine != null) {
                throw new IllegalStateException(fileName + ": there should be no more lines");
            }
        } catch (IOException | NumberFormatException | IllegalStateException e) {
            e.printStackTrace();
            catchExc = e;
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
                finallyExc = e;
            }
        }

        if (catchExc != null) {
            throw new RuntimeException(catchExc);
        } else if (finallyExc != null) {
            throw new RuntimeException(finallyExc);
        }

        // INVARIANT: probePositionIDCfg != null
        return probePositionIDCfg;
    }


    /**
     * Update edge-pair requirements (this includes even individual edges
     * as well. This is done to make edge-pairs test requirements subsume edges test
     * requirements).
     * <p>
     * Note that the edge pairs that already exist are not changed. i.e., if the edge-pairs
     * were covered, this will not change, and if the edge-pairs were not covered, they will
     * remain uncovered. Only new edge-pairs can be added, but the old ones remain the same.
     */
    private void updateEdgePairRequirements() {
        // add all nodes
        for (Node<ProbePositionID> node : getAllNodes()) {
            NodeTR<ProbePositionID> nodeReq = new NodeTR<>(node);
            nodeReqs.putIfAbsent(nodeReq, nodeReq);
        }

        // add all edges
        for (Edge<ProbePositionID, FlowType> edge : getAllEdges()) {
            EdgeTR<ProbePositionID> edgeReq = new EdgeTR<>(new Edge<>(edge.getSource(), edge.getDestination()));
            edgesReqs.putIfAbsent(edgeReq, edgeReq);
        }

        // add all (incoming, outgoing) pairs in edgePairsTestRequirements
        for (Node<ProbePositionID> node : getAllNodes()) {
            for (Edge<ProbePositionID, FlowType> incoming : incomingEdges(node)) {
                for (Edge<ProbePositionID, FlowType> outgoing : outgoingEdges(node)) {
                    EdgePairTR<ProbePositionID> edgePair = new EdgePairTR<>(
                            new Edge<>(incoming.getSource(), incoming.getDestination()),
                            new Edge<>(outgoing.getSource(), outgoing.getDestination())
                    );
                    edgePairsReqs.putIfAbsent(edgePair, edgePair);
                }
            }
        }
    }

    /**
     * Update all test requirements of type:
     * <p>
     * - edge-pair (this includes even individual edges
     * as well. This is done to make edge-pairs test requirements subsume edges test
     * requirements).
     * <p>
     * Update edge-pair requirements. Note that the edge pairs that already exist are
     * not changed. i.e., if the edge-pairs were covered, this will not change, and
     * if the edge-pairs were not covered, they will remain uncovered. Only new edge-pairs
     * can be added, but the old ones remain the same.
     */
    public void updateTestRequirements() {
        if (graphNotHaveUniqueEdges()) {
            throw new IllegalStateException(this.filePath + ": graph should have unique edges for updating test reqs to work");
        }

        updateEdgePairRequirements();
    }

    /**
     * Cover edge-pair test requirements that path covers.
     */
    private void coverEdgePairs(List<Node<ProbePositionID>> path) {
//        printToOut("path: ");
//        printToOut(path.toString());

        // cover all nodes
        for (Node<ProbePositionID> node : path) {
            NodeTR<ProbePositionID> nodeReq = nodeReqs.get(new NodeTR<>(node));

            if (nodeReq == null) {
                printToErr(this.filePath + ":[ERROR-NC]: node: " + node);
            } else {
                nodeReq.setCovered(true);
            }
        }

        // cover all edges - This should be printed.
        for (int i = 1; i < path.size(); i++) {
            Edge<ProbePositionID, Object> key = new Edge<>(path.get(i - 1), path.get(i));
            EdgeTR<ProbePositionID> edge = edgesReqs.get(new EdgeTR<>(key));

            if (edge == null) {
                printToErr(this.filePath + ":[ERROR-EC]: edge: (" + path.get(i - 1) + "," + path.get(i) + ") does not exist");
            } else {
                edge.setCovered(true);
            }
        }

        // cover all edge-pairs
        for (int i = 2; i < path.size(); i++) {
            Edge<ProbePositionID, Object> edge1 = new Edge<>(path.get(i - 2), path.get(i - 1));
            Edge<ProbePositionID, Object> edge2 = new Edge<>(path.get(i - 1), path.get(i));

            EdgePairTR<ProbePositionID> edgePair = edgePairsReqs.get(new EdgePairTR<>(edge1, edge2));

            if (edgePair == null) {
                printToErr(String.format("%s:[ERROR-EPC]: edge pair: {(%s, %s), (%s, %s)} does not exist", this.filePath,
                        path.get(i - 2), path.get(i - 1), path.get(i - 1), path.get(i)));
            } else {
                edgePair.setCovered(true);
            }
        }

//        edgePairsReqs.forEach((key, value) -> printToOut(value.toString()));
    }

    /**
     * Covers test requirements that path covers.
     */
    public void coverTestRequirements(List<Node<ProbePositionID>> path) {
        if (graphNotHaveUniqueEdges()) {
            throw new IllegalStateException(this.filePath + ": graph should have unique edges for covering test reqs to work");
        }

        coverEdgePairs(path);
    }

    private boolean graphNotHaveUniqueEdges() {
        Map<Tuple<Integer, Integer>, List<Edge<ProbePositionID, FlowType>>> edgeGroups = this.getAllEdges().stream().
                collect(Collectors.groupingBy(edge ->
                        new Tuple<>(edge.getSource().getData().getId(), edge.getDestination().getData().getId())));

        return !edgeGroups.values().stream().allMatch(edges -> edges.size() == 1);
    }

//    /**
//     * Add edge pair coverage (includes individual edges as well) in a string builder
//     */
//    private List<String> getEdgePairsCoverageInfo(List<String> stringList) {
//        // calculate edges coverage
//        int noOfEdgesCovered = edgesReqs.keySet().stream().
//                filter(AbstractTestRequirement::isCovered).
//                collect(Collectors.toSet()).size();
//        int totalNoOfEdges = edgesReqs.size();
//        double percentOfEdgesCovered = noOfEdgesCovered * 1.0d / totalNoOfEdges * 100d;
//        stringList.add("Edges covered: " + noOfEdgesCovered + ", total edges: " + totalNoOfEdges +
//                ", percent: " + percentOfEdgesCovered + " %");
//
//        // calculate edge-pair coverage as well
//        int noOfEdgePairsCovered = edgePairsReqs.keySet().stream().
//                filter(AbstractTestRequirement::isCovered).
//                collect(Collectors.toSet()).size();
//        int totalNoOfEdgePairs = edgePairsReqs.size();
//        double percentOfEdgePairsCovered = noOfEdgePairsCovered * 1.0d / totalNoOfEdgePairs * 100d;
//        stringList.add("Edge-pairs covered: " + noOfEdgePairsCovered + ", total edges: " + totalNoOfEdgePairs +
//                ", percent: " + percentOfEdgePairsCovered + " %");
//
//        // calculate edge-pair and edges in total
//        int noOfBothCovered = noOfEdgesCovered + noOfEdgePairsCovered;
//        int totalOfBoth = totalNoOfEdges + totalNoOfEdgePairs;
//        double percentOfBoth = noOfBothCovered * 1.0d / totalOfBoth * 100d;
//        stringList.add("Edge-pairs (including edges) covered: " + noOfBothCovered + ", total (both): " + totalOfBoth +
//                ", percent: " + percentOfBoth + " %");
//
//        return stringList;
//    }

//    /**
//     * Print coverage info to a given print stream.
//     */
//    public List<String> getCoverageInfo() {
//        List<String> stringList = new ArrayList<>();
//        return getEdgePairsCoverageInfo(stringList);
//    }

    public Map<String, Integer> getCoverageInfoKeyPairs() {
        Map<String, Integer> kp = new HashMap<>();

        kp.put(NODES_COVERED, nodeReqs.keySet().stream().
                filter(AbstractTestRequirement::isCovered).
                collect(Collectors.toSet()).size());
        kp.put(TOTAL_NODES, nodeReqs.size());

        kp.put(EDGES_COVERED, edgesReqs.keySet().stream().
                filter(AbstractTestRequirement::isCovered).
                collect(Collectors.toSet()).size());
        kp.put(TOTAL_EDGES, edgesReqs.size());

        kp.put(EDGE_PAIRS_COVERED, edgePairsReqs.keySet().stream().
                filter(AbstractTestRequirement::isCovered).
                collect(Collectors.toSet()).size());
        kp.put(TOTAL_EDGE_PAIRS, edgePairsReqs.size());

        return kp;
    }

    public String getRelativeFilePath() {
        return filePath;
    }

    public String getFullMethodName() {
        return fullMethodName;
    }
}
