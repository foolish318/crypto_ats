package com.example.hft.datasource.deepbook.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


final class RawJournalWriter implements AutoCloseable {
    static final String JOURNAL_VERSION = "RAW_JOURNAL_V1";

    private final Path basePath;
    private final Path indexPath;
    private final ObjectMapper mapper;
    private final RawJournalConfig config;
    private final List<Path> segments = new ArrayList<>();

    private FileOutputStream output;
    private FileChannel channel;
    private BufferedWriter writer;
    private Path currentPath;
    private int segmentIndex;
    private long nextFrameIndex;
    private long segmentFirstFrame;
    private long segmentRecords;
    private long segmentBytes;
    private long openedEpochMillis;
    private long recordsSinceFlush;
    private long recordsSinceFsync;
    private long lastWriteLagNanos;
    private long maxWriteLagNanos;
    private boolean closed;

    RawJournalWriter(Path basePath, ObjectMapper mapper, RawJournalConfig config) throws Exception {
        this.basePath = basePath;
        this.indexPath = Path.of(basePath.toString() + ".index");
        this.mapper = mapper;
        this.config = config;
        Path parent = basePath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(indexPath);
        enforceDiskSpace();
        openSegment();
    }

    void write(RawEnvelope envelope, long enqueuedNanos) throws Exception {
        ensureOpen();
        long now = System.currentTimeMillis();
        RawJournalFrame frame = new RawJournalFrame(
                RawJournalFrameType.RECORD,
                JOURNAL_VERSION,
                segmentIndex,
                nextFrameIndex,
                now,
                "",
                RawJournalChecksum.record(mapper, envelope),
                envelope
        );
        String line = mapper.writeValueAsString(frame);
        long lineBytes = utf8LineBytes(line);
        if (segmentRecords > 0L
                && (segmentBytes + lineBytes > config.maxSegmentBytes()
                || now - openedEpochMillis >= config.maxSegmentDuration().toMillis())) {
            rotate();
            frame = new RawJournalFrame(
                    RawJournalFrameType.RECORD,
                    JOURNAL_VERSION,
                    segmentIndex,
                    nextFrameIndex,
                    now,
                    "",
                    RawJournalChecksum.record(mapper, envelope),
                    envelope
            );
            line = mapper.writeValueAsString(frame);
            lineBytes = utf8LineBytes(line);
        }
        writeLine(line, lineBytes);
        segmentRecords++;
        nextFrameIndex++;
        recordsSinceFlush++;
        recordsSinceFsync++;
        if ((nextFrameIndex & 1_023L) == 0L) {
            enforceDiskSpace();
        }
        lastWriteLagNanos = Math.max(0L, System.nanoTime() - enqueuedNanos);
        maxWriteLagNanos = Math.max(maxWriteLagNanos, lastWriteLagNanos);
        if (recordsSinceFlush >= config.flushEveryRecords()) {
            writer.flush();
            recordsSinceFlush = 0L;
        }
        if (recordsSinceFsync >= config.fsyncEveryRecords()) {
            writer.flush();
            channel.force(false);
            recordsSinceFsync = 0L;
        }
    }

    String currentSegment() {
        return currentPath == null ? "" : currentPath.toString();
    }

    long diskUsageBytes() {
        long bytes = 0L;
        for (Path segment : segments) {
            try {
                bytes += Files.size(segment);
            } catch (Exception ignored) {
                // Summary remains available even when a segment disappears externally.
            }
        }
        try {
            bytes += Files.exists(indexPath) ? Files.size(indexPath) : 0L;
        } catch (Exception ignored) {
            // See above.
        }
        return bytes;
    }

    long lastWriteLagNanos() {
        return lastWriteLagNanos;
    }

    long maxWriteLagNanos() {
        return maxWriteLagNanos;
    }

    private void rotate() throws Exception {
        closeSegment();
        segmentIndex++;
        enforceDiskSpace();
        enforceRetention();
        openSegment();
    }

    private void openSegment() throws Exception {
        currentPath = segmentPath(basePath, segmentIndex);
        output = new FileOutputStream(currentPath.toFile(), false);
        channel = output.getChannel();
        writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        segments.add(currentPath);
        openedEpochMillis = System.currentTimeMillis();
        segmentFirstFrame = nextFrameIndex;
        segmentRecords = 0L;
        segmentBytes = 0L;
        recordsSinceFlush = 0L;
        recordsSinceFsync = 0L;
        RawJournalFrame header = new RawJournalFrame(
                RawJournalFrameType.HEADER,
                JOURNAL_VERSION,
                segmentIndex,
                nextFrameIndex,
                openedEpochMillis,
                config.sourceMetadata(),
                RawJournalChecksum.text(config.sourceMetadata()),
                null
        );
        String line = mapper.writeValueAsString(header);
        writeLine(line, utf8LineBytes(line));
    }

    private void closeSegment() throws Exception {
        if (writer == null) {
            return;
        }
        writer.flush();
        channel.force(false);
        writer.close();
        long closedAt = System.currentTimeMillis();
        RawJournalIndexEntry entry = new RawJournalIndexEntry(
                segmentIndex,
                currentPath.getFileName().toString(),
                segmentFirstFrame,
                segmentRecords == 0L ? segmentFirstFrame : nextFrameIndex - 1L,
                segmentRecords,
                segmentBytes,
                openedEpochMillis,
                closedAt
        );
        Files.writeString(
                indexPath,
                mapper.writeValueAsString(entry) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        writer = null;
        channel = null;
        output = null;
    }

    private void writeLine(String line, long bytes) throws Exception {
        writer.write(line);
        writer.newLine();
        segmentBytes += bytes;
    }

    private void enforceDiskSpace() throws Exception {
        Path probe = basePath.toAbsolutePath().getParent();
        if (probe == null) {
            probe = Path.of(".").toAbsolutePath();
        }
        FileStore store = Files.getFileStore(probe);
        if (store.getUsableSpace() < config.minimumFreeDiskBytes()) {
            throw new IllegalStateException(
                    "raw journal disk space below minimum " + config.minimumFreeDiskBytes());
        }
    }

    private void enforceRetention() {
        long cutoff = Instant.now().minus(config.retention()).toEpochMilli();
        for (Path segment : List.copyOf(segments)) {
            if (segment.equals(currentPath)) {
                continue;
            }
            try {
                if (Files.getLastModifiedTime(segment).toMillis() < cutoff) {
                    Files.deleteIfExists(segment);
                    segments.remove(segment);
                }
            } catch (Exception ignored) {
                // Retention failure is observable through disk usage and can retry on rotation.
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        closeSegment();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("raw journal is closed");
        }
    }

    static Path segmentPath(Path base, int segmentIndex) {
        if (segmentIndex == 0) {
            return base;
        }
        String file = base.getFileName().toString();
        int extension = file.lastIndexOf('.');
        String stem = extension < 0 ? file : file.substring(0, extension);
        String suffix = extension < 0 ? "" : file.substring(extension);
        return base.resolveSibling(
                stem + ".segment-" + String.format("%06d", segmentIndex) + suffix
        );
    }

    private static long utf8LineBytes(String line) {
        return line.getBytes(StandardCharsets.UTF_8).length
                + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
    }
}
