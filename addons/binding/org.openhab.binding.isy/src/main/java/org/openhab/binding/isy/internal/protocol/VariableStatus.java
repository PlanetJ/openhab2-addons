package org.openhab.binding.isy.internal.protocol;

import java.util.List;
import java.util.Map;

import org.openhab.binding.isy.internal.protocol.StateVariable.ValueType;

import com.google.common.collect.Maps;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

@XStreamAlias("vars")
public class VariableStatus {

    @XStreamImplicit(itemFieldName = "var")
    private List<StateVariable.ValueType> variables;

    private Map<String, ValueType> varMap;

    public List<StateVariable.ValueType> getVariables() {
        return variables;
    }

    public void setVariables(List<StateVariable.ValueType> variables) {
        this.variables = variables;
    }

    public void index() {
        if (variables != null) {
            Map<String, StateVariable.ValueType> newMap = Maps.newHashMap();
            for (ValueType val : variables) {
                newMap.put(val.getId(), val);
            }
            varMap = newMap;
        }
    }

    public ValueType getVariable(String id) {
        return varMap != null ? varMap.get(id) : null;
    }

}
