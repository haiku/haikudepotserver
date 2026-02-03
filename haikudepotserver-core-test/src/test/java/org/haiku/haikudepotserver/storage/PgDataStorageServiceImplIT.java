/*
 * Copyright 2025-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.assertj.core.api.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.support.PgDataStorageTestHelper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.*;

@ContextConfiguration(classes = TestConfig.class)
public class PgDataStorageServiceImplIT extends AbstractIntegrationTest {

    @Resource
    private DataSource dataSource;

    private PgDataStorageServiceImpl storageImpl;

    private final Random random = new Random();

    @PostConstruct
    public void init() {
        this.storageImpl = new PgDataStorageServiceImpl(dataSource, Mockito.mock(MeterRegistry.class), 32);
    }

    /**
     * <p>Sets up some storage and then deletes the item.</p>
     */
    @Test
    public void testDelete() throws IOException, SQLException {
        //GIVEN
        String code = UUID.randomUUID().toString();
        ByteSink byteSink = storageImpl.put(code);
        byte[] buffer = new byte[172]; // will be many body parts

        try (OutputStream outputStream = byteSink.openStream()) {
            outputStream.write(buffer);
        }

        // WHEN
        storageImpl.remove(code);

        // THEN
        Assertions.assertThat(hasData(code)).isFalse();
    }

    @Test
    public void testStoreData_inFull() throws IOException, SQLException {
        ByteSink byteSink = storageImpl.put("ABC123DEF");
        byte[] buffer = new byte[172];

        random.nextBytes(buffer);
        HashCode expectedHash = Hashing.sha256().hashBytes(buffer);

        // write the data.

        try (OutputStream outputStream = byteSink.openStream()) {
            outputStream.write(buffer);
        }

        // assert that the data structures in the database were written OK.

        List<byte[]> parts = getDatasForCode("ABC123DEF");

        Assertions.assertThat(parts).hasSize(6);

        for (int i = 0; i < 5; i++) {
            Assertions.assertThat(parts.get(i).length).isEqualTo(32);
        }

        Assertions.assertThat(parts.get(5).length).isEqualTo(12);

        Hasher hasher = Hashing.sha256().newHasher();
        parts.forEach(part -> hasher.putBytes(part));
        Assertions.assertThat(hasher.hash()).isEqualTo(expectedHash);
    }

    @Test
    public void testStoreData_inVariousChunks() throws IOException, SQLException {
        ByteSink byteSink = storageImpl.put("ABC123GHI");
        byte[] buffer = new byte[128];

        random.nextBytes(buffer);
        HashCode expectedHash = Hashing.sha256().hashBytes(buffer);

        // write the data.

        try (OutputStream outputStream = byteSink.openStream()) {
            outputStream.write(buffer, 0, 16);
            outputStream.write(buffer, 16, 48);
            outputStream.write(buffer, 64, 64);
        }

        // assert that the data structures in the database were written OK.

        List<byte[]> parts = getDatasForCode("ABC123GHI");

        Assertions.assertThat(parts).hasSize(4);

        for (int i = 0; i < 4; i++) {
            Assertions.assertThat(parts.get(i).length).isEqualTo(32);
        }

        Hasher hasher = Hashing.sha256().newHasher();
        parts.forEach(part -> hasher.putBytes(part));
        Assertions.assertThat(hasher.hash()).isEqualTo(expectedHash);
    }

    /**
     * <p>This situation checks to see what happens when the data is written in consistent sized chunks and is then
     * read in consistent sized read operations.</p>
     */
    @Test
    public void testReadData_writtenInConsistentChunks() throws IOException, SQLException {
        byte[] buffer = new byte[172];
        random.nextBytes(buffer);
        HashCode expectedHash = Hashing.sha256().hashBytes(buffer);

        setupDatas("GHI987UTR", List.of(
                Arrays.copyOfRange(buffer, 0, 32),
                Arrays.copyOfRange(buffer, 32, 64),
                Arrays.copyOfRange(buffer, 64, 96),
                Arrays.copyOfRange(buffer, 96, 128),
                Arrays.copyOfRange(buffer, 128, 160),
                Arrays.copyOfRange(buffer, 160, 172)
        ));

        ByteSource byteSource = storageImpl.get("GHI987UTR").orElseThrow();
        Assertions.assertThat(hashByteSource(byteSource)).isEqualTo(expectedHash);
    }

    /**
     * <p>This situation checks to see what happens when the data is written in different sized chunks and is then read
     * in consistent sized read operations.</p>
     */
    @Test
    public void testReadData_writtenInDifferentChunks() throws IOException, SQLException {
        byte[] buffer = new byte[128];
        random.nextBytes(buffer);
        HashCode expectedHash = Hashing.sha256().hashBytes(buffer);

        setupDatas("GHI987PQR", List.of(
                Arrays.copyOfRange(buffer, 0, 16),
                Arrays.copyOfRange(buffer, 16, 64),
                Arrays.copyOfRange(buffer, 64, 128)
        ));

        ByteSource byteSource = storageImpl.get("GHI987PQR").orElseThrow();
        Assertions.assertThat(hashByteSource(byteSource)).isEqualTo(expectedHash);
    }

    /**
     * <p>This situation checks to see what happens when the data is written in different sized chunks and is then read
     * in consistent sized read operations.</p>
     */
    @Test
    public void testReadData_readInDifferentChunks() throws IOException, SQLException {
        byte[] buffer = new byte[172];
        random.nextBytes(buffer);
        HashCode expectedHash = Hashing.sha256().hashBytes(buffer);

        setupDatas("GHI987ASD", List.of(
                Arrays.copyOfRange(buffer, 0, 32),
                Arrays.copyOfRange(buffer, 32, 64),
                Arrays.copyOfRange(buffer, 64, 96),
                Arrays.copyOfRange(buffer, 96, 128),
                Arrays.copyOfRange(buffer, 128, 160),
                Arrays.copyOfRange(buffer, 160, 172)
        ));

        ByteSource byteSource = storageImpl.get("GHI987ASD").orElseThrow();

        Hasher hasher = Hashing.sha256().newHasher();

        try (InputStream inputStream = byteSource.openStream()) {

            int[] readLengths = new int[] { 16, 63, 34, 17, 42 };

            for (int readLength : readLengths) {
                Assertions.assertThat(readLength == inputStream.read(buffer, 0, readLength));
                hasher.putBytes(buffer, 0, readLength);
            }
        }

        Assertions.assertThat(hasher.hash()).isEqualTo(expectedHash);
    }

    @Test
    public void testReadData_zeroLength() throws IOException, SQLException {
        setupDatas("GHI987EMP", List.of());

        ByteSource byteSource = storageImpl.get("GHI987EMP").orElseThrow();

        byte[] buffer = new byte[128];

        try (InputStream inputStream = byteSource.openStream()) {
            int read = inputStream.read(buffer, 0, 32);
            Assertions.assertThat(read).isEqualTo(-1);
        }
    }

    /**
     * <p>Test what happens when there is no data stored under a code.</p>
     */
    @Test
    public void testReadData_missing() throws IOException {
        Optional<? extends ByteSource> byteSourceOptional = storageImpl.get("GHI987PQR");
        Assertions.assertThat(byteSourceOptional).isEmpty();

    }

    private boolean hasData(String code) throws SQLException {
        String sql = "SELECT COUNT(oh.id) FROM datastore.object_head oh WHERE oh.code = ?";

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ) {
            preparedStatement.setString(1, code);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return 1 == resultSet.getInt(1);
                }
            }
        }

        throw new IllegalStateException("query should have returned a value.");
    }

    private HashCode hashByteSource(ByteSource byteSource) throws IOException {
        byte[] buffer = new byte[32];
        Hasher hasher = Hashing.sha256().newHasher();

        try (InputStream inputStream = byteSource.openStream()) {
            int read;

            while ((read = inputStream.read(buffer, 0, 32)) > 0) {
                hasher.putBytes(buffer, 0, read);
            }
        }

        return hasher.hash();
    }

    /**
     * <p>This will write data to database under a code so that the read operations can be performed against it.</p>
     */

    private void setupDatas(String code, List<byte[]> datas) throws SQLException {
        PgDataStorageTestHelper.setupDatas(
                dataSource,
                new java.sql.Timestamp(Clock.systemUTC().millis()),
                code,
                datas);
    }

    /**
     * <p>Obtains the data from the database out of the storage tables. This can be used to check write operations are
     * working properly.</p>
     */

    private List<byte[]> getDatasForCode(String code) throws SQLException {
        List<byte[]> result = new ArrayList<>();
        String sql = """
                SELECT op.data FROM datastore.object_part op
                    JOIN datastore.object_head oh ON oh.id = op.object_head_id
                WHERE oh.code = ? ORDER BY op.ordering ASC
                """;

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)
        ) {
            preparedStatement.setString(1, code);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getBytes(1));
                }
            }
        }

        return result;
    }

}
