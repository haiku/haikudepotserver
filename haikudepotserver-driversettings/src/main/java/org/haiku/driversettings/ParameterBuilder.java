/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.driversettings;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParameterBuilder {

    private String name = null;
    private final List<String> values = new ArrayList<>();
    private List<Parameter> parameters = Collections.emptyList();

    private ParameterBuilder() {
    }

    public static ParameterBuilder newBuilder() {
        return new ParameterBuilder();
    }

    public void withWord(String word) {
        if (null == name) {
            name = word;
        } else {
            values.add(word);
        }
    }

    public void withParameters(List<Parameter> parameters) {
        this.parameters = null == parameters ? Collections.emptyList() : parameters;
    }

    public boolean hasName() {
        return null != name;
    }

    public Parameter build() throws DriverSettingsException {
        if (null == name) {
            throw new DriverSettingsException("missing name");
        }

        return new Parameter(name, ImmutableList.copyOf(values), ImmutableList.copyOf(parameters));
    }

}
