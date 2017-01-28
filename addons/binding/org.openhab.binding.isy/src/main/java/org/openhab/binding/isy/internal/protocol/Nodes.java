package org.openhab.binding.isy.internal.protocol;

import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

@XStreamAlias("nodes")
public class Nodes {

    @XStreamImplicit(itemFieldName = "node")
    private List<Node> nodes;

    @XStreamImplicit(itemFieldName = "group")
    private List<Group> groups;

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

}
