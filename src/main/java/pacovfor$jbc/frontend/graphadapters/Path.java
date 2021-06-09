package pacovfor$jbc.frontend.graphadapters;

import pacovfor$jbc.analysis.nodetypes.ProbePositionID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Path {

    private final List<ProbePositionID> path;

    public Path() {
        this.path = new ArrayList<>();
    }

    public void addProbePositionID(String id) {
//        System.out.println("adding to path... " + id);
        this.path.add(new ProbePositionID(Integer.parseInt(id)));
    }

    public List<ProbePositionID> getPath() {
        return Collections.unmodifiableList(path);
    }

}
