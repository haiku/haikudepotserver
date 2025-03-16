/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.repository;

import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.CapturingMailSender;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ContextConfiguration(classes = TestConfig.class)
public class RepositoryServiceImplIT extends AbstractIntegrationTest {

    @Resource
    private RepositoryService repositoryService;

    @Resource
    private CapturingMailSender mailSender;

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private DataSource dataSource;

    @Test
    public void testAlertForRepositoriesAbsentUpdates_noAlert() {

        ObjectContext context = serverRuntime.newContext();
        IntegrationTestSupportService.StandardTestData standardTestData = integrationTestSupportService.createStandardTestData();

        {
            RepositorySource repositorySource = RepositorySource.getByCode(context, standardTestData.repositorySource.getCode());
            repositorySource.setExpectedUpdateFrequencyHours(1);
            context.commitChanges();
        }

        // ---------------------------------
        repositoryService.alertForRepositoriesAbsentUpdates(context);
        // ---------------------------------

        Assertions.assertThat(mailSender.getSentMessages().size()).isEqualTo(0);
    }

    @Test
    public void testAlertForRepositoriesAbsentUpdates_alert() {

        ObjectContext context = serverRuntime.newContext();
        IntegrationTestSupportService.StandardTestData standardTestData = integrationTestSupportService.createStandardTestData();

        {
            RepositorySource repositorySource = RepositorySource.getByCode(context, standardTestData.repositorySource.getCode());
            repositorySource.setExpectedUpdateFrequencyHours(1);
            context.commitChanges();
        }

        // set all the PkgVersions to having been updated 2 hours ago.

        {
            java.sql.Timestamp twoHoursAgo = new java.sql.Timestamp(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));
            setAllPkgVersionModifyAndImportTimestamps(twoHoursAgo);
        }

        // ---------------------------------
        repositoryService.alertForRepositoriesAbsentUpdates(context);
        // ---------------------------------

        List<SimpleMailMessage> messages = mailSender.getSentMessages();

        Assertions.assertThat(messages).hasSize(1);

        SimpleMailMessage message = messages.getLast();
        String[] to = message.getTo();

        Assertions.assertThat(message.getFrom()).isEqualTo("integration-test-sender@example.com");
        Assertions.assertThat(to.length).isEqualTo(1);
        Assertions.assertThat(to[0]).isEqualTo("repository-absent-updates@example.com");
        Assertions.assertThat(message.getSubject()).isEqualTo("Haiku Depot Server; Absent Repository Update");
        Assertions.assertThat(message.getText()).contains("`testreposrc_xyz` expected within 1 hours; has been 2 hours");
    }

    /**
     * <p>This has to be done outside of Cayenne because there are automations within Cayenne which will also set the
     * modify timestamp.</p>
     */

    private void setAllPkgVersionModifyAndImportTimestamps(java.sql.Timestamp moment) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement
                        = connection.prepareStatement("UPDATE haikudepot.pkg_version SET modify_timestamp = ?, import_timestamp = ?")
                ) {
            statement.setTimestamp(1, moment);
            statement.setTimestamp(2, moment);
            Assertions.assertThat(statement.executeUpdate()).isGreaterThan(0);
        } catch (SQLException se) {
            throw new RuntimeException("unable to set all PkgVersion modify timestamps", se);
        }
    }

}
