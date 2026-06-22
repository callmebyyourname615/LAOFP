package com.example.switching.reportdelivery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "switching.phase-ii.report-delivery",
        name = "enabled",
        havingValue = "true")
public class SftpDeliveryService implements ReportDeliveryChannel {

    private static final Pattern SAFE_HOST = Pattern.compile("^[A-Za-z0-9.-]+$");
    private static final Pattern SAFE_USER = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Pattern SAFE_REMOTE_PATH = Pattern.compile("^/[A-Za-z0-9._/-]+$");

    private final ReportDestinationResolver resolver;

    public SftpDeliveryService(ReportDestinationResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.SFTP;
    }

    @Override
    public DeliveryResult deliver(
            ReportArtifactService.StoredArtifact artifact,
            Map<String, Object> destination) {
        String host = resolver.required(destination, "host");
        String username = resolver.required(destination, "username");
        String remoteDirectory = resolver.required(destination, "path");
        String privateKeyPath = resolver.secretReference(destination, "privateKeyPath");
        String knownHostsPath = resolver.required(destination, "knownHostsPath");
        int port = parsePort(destination.getOrDefault("port", 22));

        validateHost(host);
        validateUsername(username);
        validateRemotePath(remoteDirectory);
        validateLocalFile(privateKeyPath, "private key");
        validateLocalFile(knownHostsPath, "known-hosts file");

        Path localArtifact = null;
        Path batchFile = null;
        try {
            localArtifact = Files.createTempFile("switching-report-", ".bin");
            Files.write(localArtifact, artifact.content());

            String remotePath = joinRemote(remoteDirectory, safeFilename(artifact.fileName()));
            String temporaryRemotePath = remotePath + ".tmp";
            batchFile = Files.createTempFile("switching-sftp-batch-", ".txt");
            Files.writeString(
                    batchFile,
                    "put " + quoteBatchPath(localArtifact.toString()) + " "
                            + quoteBatchPath(temporaryRemotePath) + System.lineSeparator()
                            + "rename " + quoteBatchPath(temporaryRemotePath) + " "
                            + quoteBatchPath(remotePath) + System.lineSeparator(),
                    StandardCharsets.UTF_8);

            List<String> command = List.of(
                    "sftp",
                    "-b", batchFile.toString(),
                    "-P", Integer.toString(port),
                    "-i", privateKeyPath,
                    "-o", "BatchMode=yes",
                    "-o", "IdentitiesOnly=yes",
                    "-o", "StrictHostKeyChecking=yes",
                    "-o", "UserKnownHostsFile=" + knownHostsPath,
                    username + "@" + host);

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("SFTP delivery timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("SFTP delivery failed with exit " + process.exitValue());
            }
            return new DeliveryResult("sftp://" + host + ":" + port + remotePath);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SFTP delivery interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("SFTP delivery failed", exception);
        } finally {
            deleteQuietly(localArtifact);
            deleteQuietly(batchFile);
        }
    }

    private static int parsePort(Object rawPort) {
        int port;
        try {
            port = Integer.parseInt(String.valueOf(rawPort));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid SFTP port", exception);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Invalid SFTP port");
        }
        return port;
    }

    private static void validateHost(String host) {
        if (!SAFE_HOST.matcher(host).matches()
                || host.equalsIgnoreCase("localhost")
                || host.startsWith("127.")) {
            throw new IllegalArgumentException("Unsafe SFTP host");
        }
    }

    private static void validateUsername(String username) {
        if (!SAFE_USER.matcher(username).matches()) {
            throw new IllegalArgumentException("Unsafe SFTP username");
        }
    }

    private static void validateRemotePath(String path) {
        if (!SAFE_REMOTE_PATH.matcher(path).matches() || path.contains("..")) {
            throw new IllegalArgumentException("Unsafe SFTP remote path");
        }
    }

    private static void validateLocalFile(String path, String description) {
        Path candidate = Path.of(path).normalize();
        if (!candidate.isAbsolute() || path.contains("..") || !Files.isRegularFile(candidate)) {
            throw new IllegalArgumentException("Invalid SFTP " + description);
        }
    }

    private static String joinRemote(String directory, String filename) {
        return directory.endsWith("/") ? directory + filename : directory + "/" + filename;
    }

    private static String safeFilename(String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+") || filename.contains("..")) {
            throw new IllegalArgumentException("Unsafe report filename");
        }
        return filename;
    }

    private static String quoteBatchPath(String value) {
        // OpenSSH sftp batch supports double-quoted paths; inputs have already been constrained.
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Temporary-file cleanup must not hide the delivery result.
        }
    }
}
