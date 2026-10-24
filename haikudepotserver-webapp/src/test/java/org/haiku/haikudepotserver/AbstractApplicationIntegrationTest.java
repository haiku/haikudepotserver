package org.haiku.haikudepotserver;

import org.haiku.haikudepotserver.config.ApplicationTestConfig;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ApplicationTestConfig.class)
public class AbstractApplicationIntegrationTest extends AbstractIntegrationTest {
}
