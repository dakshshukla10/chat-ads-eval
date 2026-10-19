package dev.chatads.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One small Postgres and one Redis per test JVM, started on first use and removed by Testcontainers
 * when the JVM exits. The Docker VM is shared and tiny, so the containers are capped on memory.
 */
public final class Containers {

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> redis;

    private Containers() {
    }

    public static synchronized PostgreSQLContainer<?> postgres() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("chatads")
                    .withUsername("chatads")
                    .withPassword("test")
                    .withCommand("postgres", "-c", "shared_buffers=32MB", "-c", "max_connections=30", "-c", "fsync=off")
                    .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(256L * 1024 * 1024));
            postgres.start();
        }
        return postgres;
    }

    public static synchronized GenericContainer<?> redis() {
        if (redis == null) {
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(64L * 1024 * 1024));
            redis.start();
        }
        return redis;
    }

    public static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
