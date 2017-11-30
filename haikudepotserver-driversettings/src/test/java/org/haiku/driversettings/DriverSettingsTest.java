/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.driversettings;

import com.google.common.base.Charsets;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;

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

            Assert.assertThat(parameters.size(), is(6));
            assertSimpleParameter(parameters.get(0), "name", "HaikuPorts");
            assertSimpleParameter(parameters.get(1), "vendor", "Haiku Project");
            assertSimpleParameter(parameters.get(2), "summary", "The HaikuPorts repository");
            assertSimpleParameter(parameters.get(3), "priority", "1");
            assertSimpleParameter(parameters.get(4), "url", "https://vmpkg.haiku-os.org/haikuports/master/repository/x86_gcc2");
            assertSimpleParameter(parameters.get(5), "architecture", "x86_gcc2");
        }

    }

    private void assertSimpleParameter(Parameter parameter, String key, String value) {
        Assert.assertThat(parameter.getValues().size(), is(1));
        Assert.assertThat(parameter.getParameters().size(), is(0));
        Assert.assertThat(parameter.getName(), is(key));
        Assert.assertThat(parameter.getValues().get(0), is(value));
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

            Assert.assertThat(parameters.size(), is(3));

            {
                Parameter parameter0 = parameters.get(0);

                Assert.assertThat(parameter0.getName(), is("keyA"));
                Assert.assertThat(parameter0.getValues(), CoreMatchers.hasItems("b", "c", "d"));

                List<Parameter> parameters0 = parameter0.getParameters();
                Assert.assertThat(parameters0.size(), is(1));
                Parameter parameter00 = parameters0.get(0);
                Assert.assertThat(parameter00.getName(), is("keyB"));
                Assert.assertThat(parameter00.getValues().size(), is(0));

                List<Parameter> parameters00 = parameter00.getParameters();
                Assert.assertThat(parameters00.size(), is(1));
                Parameter parameter000 = parameters00.get(0);
                Assert.assertThat(parameter000.getName(), is("keyC"));
                Assert.assertThat(parameter000.getValues(), CoreMatchers.hasItems("d", "e", "f"));

                List<Parameter> parameters000 = parameter000.getParameters();
                Assert.assertThat(parameters000.size(), is(2));
                Parameter parameter0000 = parameters000.get(0);
                Parameter parameter0001 = parameters000.get(1);
                Assert.assertThat(parameter0000.getName(), is("keyD"));
                Assert.assertThat(parameter0000.getValues(), CoreMatchers.hasItems("e"));
                Assert.assertThat(parameter0001.getName(), is("keyE"));
                Assert.assertThat(parameter0001.getValues(), CoreMatchers.hasItems("f"));
            }

            {
                Parameter parameter1 = parameters.get(1);
                Assert.assertThat(parameter1.getName(), is("keyA"));
                Assert.assertThat(parameter1.getValues().size(), is(0));
                List<Parameter> parameters1 = parameter1.getParameters();
                Assert.assertThat(parameters1.size(), is(1));
                Parameter parameter10 = parameters1.get(0);
                Assert.assertThat(parameter10.getName(), is("disabled"));
                Assert.assertThat(parameter10.getValues().size(), is(0));
                Assert.assertThat(parameter10.getParameters().size(), is(0));
            }

            {
                Parameter parameter2 = parameters.get(2);
                Assert.assertThat(parameter2.getName(), is("keyA"));
                Assert.assertThat(parameter2.getValues().size(), is(2));
                Assert.assertThat(parameter2.getValues(), CoreMatchers.hasItems("=", "b"));
                List<Parameter> parameters2 = parameter2.getParameters();
                Assert.assertThat(parameters2.size(), is(1));
                Parameter parameter20 = parameters2.get(0);
                Assert.assertThat(parameter20.getName(), is("keyB=d"));
                Assert.assertThat(parameter20.getValues().size(), is(1));
                Assert.assertThat(parameter20.getValues(), CoreMatchers.hasItems("=e"));
                List<Parameter> parameters20 = parameter20.getParameters();
                Assert.assertThat(parameters20.size(), is(2));
                Parameter parameter200 = parameters20.get(0);
                Assert.assertThat(parameter200.getName(), is("keyC"));
                Assert.assertThat(parameter200.getValues().size(), is(1));
                Assert.assertThat(parameter200.getValues(), CoreMatchers.hasItems("f g"));
                Parameter parameter201 = parameters20.get(1);
                Assert.assertThat(parameter201.getName(), is("keyD"));
                Assert.assertThat(parameter201.getValues().size(), is(1));
                Assert.assertThat(parameter201.getValues(), CoreMatchers.hasItems("h"));
            }

        }

    }

}