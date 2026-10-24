package org.haiku.haikudepotserver;

import org.haiku.haikudepotserver.config.CoreTestConfig;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = CoreTestConfig.class)
public class AbstractCoreIntegrationTest extends AbstractIntegrationTest {
}
