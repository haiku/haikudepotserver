/*
 * Copyright 2017-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.driversettings;

import com.google.common.base.Charsets;
import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class DriverSettingsTest {

    /**
     * <p>This tests a typical repository info file; in this case from HaikuPorts.</p>
     */

    @Test
    public void testParseRepoInfo() throws Exception {
        try (
                InputStream inputStream = getClass().getResourceAsStream("/repo.info");
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charsets.UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader)
        ) {

            // ---------------------------
            List<Parameter> parameters = DriverSettings.parse(reader);
            // ---------------------------

            Assertions.assertThat(parameters.size()).isEqualTo(6);
            assertSimpleParameter(parameters.get(0), "name", "HaikuPorts");
            assertSimpleParameter(parameters.get(1), "vendor", "Haiku Project");
            assertSimpleParameter(parameters.get(2), "summary", "The HaikuPorts repository");
            assertSimpleParameter(parameters.get(3), "priority", "1");
            assertSimpleParameter(parameters.get(4), "url", "https://vmpkg.haiku-os.org/haikuports/master/repository/x86_gcc2");
            assertSimpleParameter(parameters.get(5), "architecture", "x86_gcc2");
        }

    }

    private void assertSimpleParameter(Parameter parameter, String key, String value) {
        Assertions.assertThat(parameter.getValues().size()).isEqualTo(1);
        Assertions.assertThat(parameter.getParameters().size()).isEqualTo(0);
        Assertions.assertThat(parameter.getName()).isEqualTo(key);
        Assertions.assertThat(parameter.getValues().get(0)).isEqualTo(value);
    }

    @Test
    public void testParseSample1() throws Exception {
        try (
                InputStream inputStream = getClass().getResourceAsStream("/sample1.txt");
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charsets.UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader)
        ) {

            // ---------------------------
            List<Parameter> parameters = DriverSettings.parse(reader);
            // ---------------------------

            Assertions.assertThat(parameters.size()).isEqualTo(3);

            {
                Parameter parameter0 = parameters.get(0);

                Assertions.assertThat(parameter0.getName()).isEqualTo("keyA");
                Assertions.assertThat(parameter0.getValues()).contains("b", "c", "d");

                List<Parameter> parameters0 = parameter0.getParameters();
                Assertions.assertThat(parameters0).hasSize(1);
                Parameter parameter00 = parameters0.get(0);
                Assertions.assertThat(parameter00.getName()).isEqualTo("keyB");
                Assertions.assertThat(parameter00.getValues()).isEmpty();;

                List<Parameter> parameters00 = parameter00.getParameters();
                Assertions.assertThat(parameters00).hasSize(1);
                Parameter parameter000 = parameters00.get(0);
                Assertions.assertThat(parameter000.getName()).isEqualTo("keyC");
                Assertions.assertThat(parameter000.getValues()).contains("d", "e", "f");

                List<Parameter> parameters000 = parameter000.getParameters();
                Assertions.assertThat(parameters000).hasSize(2);
                Parameter parameter0000 = parameters000.get(0);
                Parameter parameter0001 = parameters000.get(1);
                Assertions.assertThat(parameter0000.getName()).isEqualTo("keyD");
                Assertions.assertThat(parameter0000.getValues()).contains("e");
                Assertions.assertThat(parameter0001.getName()).isEqualTo("keyE");
                Assertions.assertThat(parameter0001.getValues()).contains("f");
            }

            {
                Parameter parameter1 = parameters.get(1);
                Assertions.assertThat(parameter1.getName()).isEqualTo("keyA");
                Assertions.assertThat(parameter1.getValues()).isEmpty();;
                List<Parameter> parameters1 = parameter1.getParameters();
                Assertions.assertThat(parameters1).hasSize(1);
                Parameter parameter10 = parameters1.get(0);
                Assertions.assertThat(parameter10.getName()).isEqualTo("disabled");
                Assertions.assertThat(parameter10.getValues()).isEmpty();
                Assertions.assertThat(parameter10.getParameters()).isEmpty();
            }

            {
                Parameter parameter2 = parameters.get(2);
                Assertions.assertThat(parameter2.getName()).isEqualTo("keyA");
                Assertions.assertThat(parameter2.getValues()).hasSize(2);
                Assertions.assertThat(parameter2.getValues()).contains("=", "b");
                List<Parameter> parameters2 = parameter2.getParameters();
                Assertions.assertThat(parameters2).hasSize(1);
                Parameter parameter20 = parameters2.get(0);
                Assertions.assertThat(parameter20.getName()).isEqualTo("keyB=d");
                Assertions.assertThat(parameter20.getValues()).hasSize(1);
                Assertions.assertThat(parameter20.getValues()).contains("=e");
                List<Parameter> parameters20 = parameter20.getParameters();
                Assertions.assertThat(parameters20).hasSize(2);
                Parameter parameter200 = parameters20.get(0);
                Assertions.assertThat(parameter200.getName()).isEqualTo("keyC");
                Assertions.assertThat(parameter200.getValues()).hasSize(1);
                Assertions.assertThat(parameter200.getValues()).contains("f g");
                Parameter parameter201 = parameters20.get(1);
                Assertions.assertThat(parameter201.getName()).isEqualTo("keyD");
                Assertions.assertThat(parameter201.getValues()).hasSize(1);
                Assertions.assertThat(parameter201.getValues()).contains("h");
            }

        }

    }

}
