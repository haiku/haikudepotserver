/*
 * Copyright 2025-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;
import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.MeterRegistry;
import org.haiku.haikudepotserver.metrics.MetricsConstants;
import org.haiku.haikudepotserver.storage.model.DataStorageException;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * <p>This is a service for storage of data in the postgres database. It will store the
 * data items into a structure that consists of a head table and then a series of ordered
 * parts each of which carries a blob.</p>
 */

public class PgDataStorageServiceImpl implements DataStorageService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(PgDataStorageServiceImpl.class);

    private final DataSource dataSource;

    private final Clock clock;

    private final long partSize;

    /**
     * <p>This is used for a metric gauge to show the rate of data transfer.</p>
     */
    private final AtomicDouble mbPerSecondTransfer;

    public PgDataStorageServiceImpl(DataSource dataSource, MeterRegistry meterRegistry, long partSize) {
        Preconditions.checkNotNull(meterRegistry);
        Preconditions.checkNotNull(dataSource);
        Preconditions.checkArgument(partSize > 0);

        this.dataSource = dataSource;
        this.partSize = partSize;

        this.mbPerSecondTransfer = new AtomicDouble();
        meterRegistry.gauge(
                MetricsConstants.GUAGE_PG_DATA_STORAGE_MEGABYTE_PER_SECOND_TRANSFER,
                this.mbPerSecondTransfer);

        this.clock = Clock.systemUTC();
    }

    @Override
    public Set<String> keys(Duration olderThanDuration) {
        try (Connection connection = dataSource.getConnection()) {
            return PgDataStorageHelper.findHeadCodes(connection, clock, olderThanDuration);
        } catch (SQLException se) {
            throw new DataStorageException("unable to get the data storage keys", se);
        }
    }

    @Override
    public ByteSink put(String key) throws IOException {
        try (Connection connection = dataSource.getConnection()) {
            return new PgDataStorageByteSink(PgDataStorageHelper.createHead(connection, clock, key));
        } catch (SQLException se) {
            throw new DataStorageException("unable to put the data storage key [%s]".formatted(key), se);
        }
    }

    @Override
    public Optional<? extends ByteSource> get(String key) throws IOException {
        try (Connection connection = dataSource.getConnection()) {
            return PgDataStorageHelper.tryGetHeadIdByCode(connection, key)
                    .map(PgDataStorageByteSource::new);
        } catch (SQLException se) {
            throw new DataStorageException("unable to put the data storage key [%s]".formatted(key), se);
        }
    }

    @Override
    public boolean remove(String key) {
        try (Connection connection = dataSource.getConnection()) {
            PgDataStorageHelper.deleteHeadAndPartsByCode(connection, key);
        } catch (SQLException se) {
            throw new DataStorageException("unable to remove the data storage key [%s]".formatted(key), se);
        }
        return true;
    }

    @Override
    public long size() {
        try (Connection connection = dataSource.getConnection()) {
            return PgDataStorageHelper.getHeadCount(connection);
        } catch (SQLException se) {
            throw new DataStorageException("unable to get the size", se);
        }
    }

    @Override
    public long totalBytes() {
        try (Connection connection = dataSource.getConnection()) {
            return PgDataStorageHelper.getHeadLengthSum(connection);
        } catch (SQLException se) {
            throw new DataStorageException("unable to get total bytes", se);
        }
    }

    @Override
    public void clear() {
        try (Connection connection = dataSource.getConnection()) {
            PgDataStorageHelper.deleteHeadsAndParts(connection);
        } catch (SQLException se) {
            throw new DataStorageException("unable to clear", se);
        }
    }

    final class PgDataStorageByteSink extends ByteSink {

        private final long headId;

        public PgDataStorageByteSink(long headId) {
            this.headId = headId;
        }

        @Override
        public OutputStream openStream() throws IOException {
            return new PgDataStorageOutputStream(headId);
        }

    }

    /**
     * <p>This class streams data to blobs. It will buffer each part into a
     * local file so that only when the file is complete, will it be written to
     * the database. This technique will prevent contention on the database
     * connections in the pool.</p>
     */

    final class PgDataStorageOutputStream extends OutputStream {

        private final long headId;

        private File bufferFile;

        private CountingOutputStream countingOutputStream = null;

        public PgDataStorageOutputStream(long headId) throws IOException {
            bufferFile = File.createTempFile("pg-datastore-out-", ".dat");
            this.headId = headId;
        }

        @Override
        public void write(int b) throws IOException {
            getBufferFileOutputStream().write(b);
            flushIfNecessary();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int offDelta = off;
            int lenRemaining = len;

            while (lenRemaining > 0) {
                CountingOutputStream os = getBufferFileOutputStream();
                int lenToWrite = (int) Math.min(partSize - os.getCount(), lenRemaining);
                os.write(b, offDelta, lenToWrite);
                flushIfNecessary();
                lenRemaining -= lenToWrite;
                offDelta += lenToWrite;
            }
        }

        @Override
        public void flush() throws IOException {
            if (null != countingOutputStream && countingOutputStream.getCount() > 0) {
                countingOutputStream.close();
                countingOutputStream = null;

                try (Connection connection = dataSource.getConnection()) {
                    PgDataStorageHelper.createPart(connection, clock, headId, bufferFile);
                } catch (SQLException se) {
                    throw new IOException("unable to write the part for head [%d]".formatted(headId), se);
                }
            }

            super.flush();
        }

        @Override
        public void close() throws IOException {
            flush();

            if (null != countingOutputStream) {
                countingOutputStream.close();
                countingOutputStream = null;
            }

            if (null != bufferFile) {
                if (!bufferFile.delete()) {
                    LOGGER.warn("unable to delete the buffer file [{}]", bufferFile);
                }
                bufferFile = null;
            }

            super.close();
        }

        private CountingOutputStream getBufferFileOutputStream() throws IOException {
            if (null == bufferFile) {
                throw new IOException("possible use of output stream after closure");
            }

            if (null == countingOutputStream) {
                countingOutputStream = new CountingOutputStream(new FileOutputStream(bufferFile, false));
            }
            return countingOutputStream;
        }

        /**
         * <p>This method will flush the accumulated data into the database if the buffer is large enough.</p>
         */
        private void flushIfNecessary() throws IOException {
            if (null != countingOutputStream && countingOutputStream.getCount() >= partSize) {
                flush();
            }
        }

    }

    final class PgDataStorageByteSource extends ByteSource {

        private final long headId;

        public PgDataStorageByteSource(long headId) {
            this.headId = headId;
        }

        @Override
        public InputStream openStream() throws IOException {
            return new PgDataStorageInputStream(headId);
        }

    }

    /**
     * <p>This class will stream out from the blobs. It will buffer each into a local file
     * such that there is less contention on the database connections.</p>
     */

    final class PgDataStorageInputStream extends InputStream {

        /**
         * <p>These are all the parts that need to be read in to fulfill the stream.</p>
         */
        private final List<PgDataStorageHelper.Part> parts;

        private int partIndex = 0;

        private File bufferFile;

        private CountingInputStream countingInputStream = null;

        public PgDataStorageInputStream(long headId) throws IOException {
            bufferFile = File.createTempFile("pg-datastore-in-", ".dat");

            try (Connection connection = dataSource.getConnection()) {
                this.parts = PgDataStorageHelper.findOrderedPartsByHeadId(connection, headId);
            } catch (SQLException se) {
                throw new IOException("unable to find the ordered parts by head id [%d]".formatted(headId), se);
            }
        }

        @Override
        public int read() throws IOException {
            InputStream partInputStream = getBufferFileInputStream();

            if (null == partInputStream) {
                return -1;
            }

            return partInputStream.read();
        }


        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int lenRemaining = len;

            while (lenRemaining > 0) {
                CountingInputStream partInputStream = getBufferFileInputStream();

                if (null == partInputStream) {
                    if (lenRemaining == len) {
                        return -1;
                    }

                    return len - lenRemaining;
                }

                int read = partInputStream.read(b, off + (len - lenRemaining), lenRemaining);

                if (-1 == read) {
                    if (lenRemaining == len) {
                        return -1;
                    }
                    return len - lenRemaining;
                }

                lenRemaining -= read;
            }

            return len - lenRemaining;
        }

        @Override
        public void close() throws IOException {

            if (null != countingInputStream) {
                countingInputStream.close();
                countingInputStream = null;
            }

            if (null != bufferFile) {
                if (!bufferFile.delete()) {
                    LOGGER.warn("unable to delete the buffer file [{}]", bufferFile);
                }
                bufferFile = null;
            }

            super.close();
        }

        private CountingInputStream getBufferFileInputStream() throws IOException {
            if (null == bufferFile) {
                throw new IOException("possible use of input stream after closure");
            }

            if (parts.isEmpty()) {
                return null;
            }

            if (null != countingInputStream && countingInputStream.getCount() >= parts.get(partIndex).length()) {
                countingInputStream.close();
                countingInputStream = null;
                partIndex++;
            }

            if (partIndex >= parts.size()) {
                return null;
            }

            if (null == countingInputStream) {
                PgDataStorageHelper.Part part = parts.get(partIndex);

                try (Connection connection = dataSource.getConnection()) {
                    PgDataStorageHelper.WriteDataPartStats stats = PgDataStorageHelper.writePartDataToFile(
                            connection, part.id(), bufferFile);
                    mbPerSecondTransfer.set(stats.megabytesPerSecond());
                } catch (SQLException se) {
                    throw new IOException("unable to write the part [%d] to file".formatted(part.id()), se);
                }

                if (part.length() != bufferFile.length()) {
                    throw new IllegalStateException(String.format(
                            "the expected part size %d is not equal to the buffer file size %d",
                            part.length(), bufferFile.length()));
                }

                countingInputStream = new CountingInputStream(new FileInputStream(bufferFile));
            }

            return countingInputStream;
        }
    }
}