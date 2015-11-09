/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.audit.events.handlers.writers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import org.forgerock.audit.events.handlers.FileBasedEventHandlerConfiguration;
import org.forgerock.audit.events.handlers.FileBasedEventHandlerConfiguration.FileRetention;
import org.forgerock.audit.events.handlers.FileBasedEventHandlerConfiguration.FileRotation;
import org.forgerock.audit.retention.DiskSpaceUsedRetentionPolicy;
import org.forgerock.audit.retention.FileNamingPolicy;
import org.forgerock.audit.retention.FreeDiskSpaceRetentionPolicy;
import org.forgerock.audit.retention.RetentionPolicy;
import org.forgerock.audit.retention.SizeBasedRetentionPolicy;
import org.forgerock.audit.retention.TimeStampFileNamingPolicy;
import org.forgerock.audit.rotation.FixedTimeRotationPolicy;
import org.forgerock.audit.rotation.RotatableObject;
import org.forgerock.audit.rotation.RotationHooks;
import org.forgerock.audit.rotation.RotationPolicy;
import org.forgerock.audit.rotation.SizeBasedRotationPolicy;
import org.forgerock.audit.rotation.TimeLimitRotationPolicy;
import org.forgerock.util.time.Duration;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an {@link RotatableWriter} that supports file rotation and retention.
 */
public class RotatableWriter implements TextWriter, RotatableObject {

    private static Logger logger = LoggerFactory.getLogger(RotatableWriter.class);

    private final List<RotationPolicy> rotationPolicies = new LinkedList<>();
    private final List<RetentionPolicy> retentionPolicies = new LinkedList<>();
    private final FileNamingPolicy fileNamingPolicy;
    private ScheduledExecutorService rotator;
    private DateTime lastRotationTime;
    private final boolean rotationEnabled;
    private final File file;
    private RotationHooks rotationHooks = new RotationHooks.NullRotatationHooks();
    private final AtomicBoolean isRotating = new AtomicBoolean(false);
    /** The underlying output stream. */
    private MeteredStream meteredStream;
    /** The underlying buffered writer using the output stream. */
    private BufferedWriter writer;
    private final ReentrantReadWriteLock writerLock = new ReentrantReadWriteLock();

    /**
     * Constructs a {@link RotatableWriter} given an initial file to manage rotation/retention, and
     * a {@link FileBasedEventHandlerConfiguration}
     * @param file The initial file to manage rotation/retention.
     * @param configuration The configuration of the rotation and retention policies.
     * @param append Whether to append to the rotatable file or not.
     */
    public RotatableWriter(final File file, final FileBasedEventHandlerConfiguration configuration,
            final boolean append) throws IOException {
        this.file = file;
        final FileRotation fileRotation = configuration.getFileRotation();
        final FileRetention fileRetention = configuration.getFileRetention();
        // Add TimeStampFileNamingPolicy
        fileNamingPolicy =
                new TimeStampFileNamingPolicy(
                        file,
                        fileRotation.getRotationFileSuffix(),
                        fileRotation.getRotationFilePrefix());

        this.rotationEnabled = fileRotation.isRotationEnabled();
        this.lastRotationTime = DateTime.now();
        this.writer = constructWriter(file, append);
        addRetentionPolicies(fileRetention);
        addRotationPolicies(fileRotation, Duration.duration(fileRotation.getRotationInterval()));
    }

    /**
     * Rotates the managed file if necessary.
     * @throws IOException If unable to rotateIfNeeded the log file.
     */
    @Override
    public void rotateIfNeeded() throws IOException {
        boolean rotationHappened = false;
        if (!rotationEnabled || isRotating.get()) {
            return;
        }
        writerLock.writeLock().lock();
        try {
            for (RotationPolicy rotationPolicy : rotationPolicies) {
                if (rotationPolicy.shouldRotateFile(this)) {
                    logger.info("Must rotate:" + file);
                    isRotating.set(true);
                    rotationHappened = rotate();
                    if (rotationHappened) {
                        logger.info("Finished rotation for:" + file);
                        break;
                    }
                }
            }
            Set<File> filesToDelete = checkRetention(); // return the files to delete, but do not delete them
            if (!filesToDelete.isEmpty()) {
                deleteFiles(filesToDelete);
            }
        } finally {
            isRotating.set(false);
            writerLock.writeLock().unlock();
        }
    }

    private boolean rotate() throws IOException {
        boolean rotationHappened = false;
        File currentFile = fileNamingPolicy.getInitialName();
        if (currentFile.exists()) {
            File newFile = fileNamingPolicy.getNextName();
            rotationHooks.preRotationAction();
            writer.close();
            if (currentFile.renameTo(newFile)) {
                rotationHappened = true;
                if (!currentFile.exists()) {
                    currentFile.createNewFile();
                    writer = constructWriter(currentFile, true);
                    rotationHooks.postRotationAction();
                }
            } else {
                logger.error("Unable to rename the audit file {}", currentFile.toString());
                writer = constructWriter(currentFile, true);
            }
            lastRotationTime = DateTime.now();
        }
        return rotationHappened;
    }

    private Set<File> checkRetention() throws IOException {
        Set<File> filesToDelete = new HashSet<>();
        for (RetentionPolicy retentionPolicy : retentionPolicies) {
            filesToDelete.addAll(retentionPolicy.deleteFiles(fileNamingPolicy));
        }
        return filesToDelete;
    }

    private void deleteFiles(final Set<File> files) {
        for (final File file : files) {
            file.delete();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytesWritten() {
        logger.info("bytes written=" + meteredStream.getBytesWritten());
        return meteredStream.getBytesWritten();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DateTime getLastRotationTime() {
        return lastRotationTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        try {
            if (rotator != null) {
                rotator.shutdown();
                rotator.awaitTermination(5L, TimeUnit.MINUTES);
            }
            writer.close();
        } catch (InterruptedException e) {
            logger.error("Unable to close the rotation thread", e);
        }
    }

    @Override
    public void shutdown() {
        try {
            close();
        } catch (IOException e) {
            logger.error("Error when performing shutdown", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerRotationHooks(final RotationHooks rotationHooks) {
        this.rotationHooks = rotationHooks;
    }

    @Override
    public void write(String str) throws IOException {
        ReadLock lock = writerLock.readLock();
        try {
            lock.lock();
            logger.info("Actually writing to file: " + str);
            writer.write(str);
        } finally {
            lock.unlock();
        }
        rotateIfNeeded();
    }

    /**
     * Forces a rotation of the writer.
     *
     * @return {@code true} if rotation was done, {@code false} otherwise.
     * @throws IOException
     *          If an error occurs
     */
    public boolean forceRotation() throws IOException {
        writerLock.writeLock().lock();
        try {
            isRotating.set(true);
            return rotate();
        }
        finally {
            isRotating.set(false);
            writerLock.writeLock().unlock();
        }
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    private BufferedWriter constructWriter(File csvFile, boolean append)
            throws IOException {
        FileOutputStream stream = new FileOutputStream(csvFile, append);
        meteredStream = new MeteredStream(stream, file.length());
        OutputStreamWriter osw = new OutputStreamWriter(meteredStream, "UTF-8");
        return new BufferedWriter(osw);
    }

    private void addRotationPolicies(final FileRotation fileRotation, final Duration duration) {
        // add SizeBasedRotationPolicy if a non zero size is supplied
        final long maxFileSize = fileRotation.getMaxFileSize();
        if (maxFileSize > 0) {
            rotationPolicies.add(new SizeBasedRotationPolicy(maxFileSize));
        }

        // add FixedTimeRotationPolicy
        final List<String> rotationTimes = fileRotation.getRotationTimes();
        if (!rotationTimes.isEmpty()) {
            rotationPolicies.add(new FixedTimeRotationPolicy(rotationTimes));
        }

        // add TimeLimitRotationPolicy if enabled
        if (!duration.isZero()){
            rotationPolicies.add(new TimeLimitRotationPolicy(duration));
            rotator = Executors.newScheduledThreadPool(1);
            rotator.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                rotateIfNeeded();
                            } catch (Exception e) {
                                logger.error("Failed to rotateIfNeeded file: {}", fileNamingPolicy.getInitialName(), e);
                            }
                        }
                    },
                    0,
                    duration.to(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
        }
    }

    private void addRetentionPolicies(final FileRetention fileRetention) {
        // Add SizeBasedRetentionPolicy if the max number of files config value is more than 0
        final int maxNumberOfHistoryFiles = fileRetention.getMaxNumberOfHistoryFiles();
        if (maxNumberOfHistoryFiles > 0) {
            retentionPolicies.add(new SizeBasedRetentionPolicy(maxNumberOfHistoryFiles));
        }

        // Add DiskSpaceUsedRetentionPolicy if config value > 0
        final long maxDiskSpaceToUse = fileRetention.getMaxDiskSpaceToUse();
        if (maxDiskSpaceToUse > 0) {
            retentionPolicies.add(new DiskSpaceUsedRetentionPolicy(maxDiskSpaceToUse));
        }

        // Add FreeDiskSpaceRetentionPolicy if config value > 0
        final long minimumFreeDiskSpace = fileRetention.getMinFreeSpaceRequired();
        if (minimumFreeDiskSpace > 0) {
            retentionPolicies.add(new FreeDiskSpaceRetentionPolicy(minimumFreeDiskSpace));
        }
    }

}