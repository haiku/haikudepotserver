/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.driversettings;

import java.util.List;
import java.util.Optional;

public class Parameter {

    private final String name;

    private final List<String> values;

    private final List<Parameter> parameters;

    public Parameter(String name, List<String> values, List<Parameter> parameters) {
        this.name = name;
        this.values = values;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public Optional<Parameter> tryGetParameter(String name) {
        return parameters.stream().filter(p -> p.getName().equals(name)).findFirst();
    }

}
