package org.haiku.haikudepotserver.support;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.support.model.Contributor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
public class ContributorsService {

    public final List<Contributor> constributors;

    public ContributorsService() {
        this(loadContributors());
    }

    private ContributorsService(List<Contributor> constributors) {
        this.constributors = constributors;
    }

    public List<Contributor> getConstributors() {
        return constributors;
    }

    private static List<Contributor> loadContributors() {
        try (InputStream inputStream = ContributorsService.class.getResourceAsStream("/contributors.properties")) {
            if(null == inputStream) {
                throw new IllegalStateException("unable to find the contributors file");
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(inputStream, Charsets.UTF_8));
            return loadContributors(properties);
        } catch (IOException ioe) {
            throw new IllegalStateException("unable to check for presence of natural language localization", ioe);
        }
    }

    private static List<Contributor> loadContributors(Properties properties) {
        return properties.entrySet().stream()
                .map(e -> createContributor(e.getKey().toString(), e.getValue().toString()))
                .collect(Collectors.toUnmodifiableList());
    }

    private static Contributor createContributor(String propertyKey, String name) {
        Preconditions.checkArgument(StringUtils.isNotBlank(propertyKey));
        Preconditions.checkArgument(StringUtils.isNotBlank(name));
        List<String> propertyKeyComponents = ImmutableList.copyOf(Splitter.on('.').split(propertyKey));
        Contributor.Type type = Contributor.Type.valueOf(propertyKeyComponents.get(0).toUpperCase());
        switch (type) {
            case ENGINEERING:
                return new Contributor(type, name);
            case LOCALIZATION:
                if (propertyKeyComponents.size() != 2) {
                    throw new IllegalStateException("bad property key [" + propertyKey + "]");
                }
                return new Contributor(type, name, propertyKeyComponents.get(1));
            default:
                throw new IllegalStateException("unknown type of contributor [" + type + "]");
        }
    }

}
