package redis.embedded.util;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.embedded.Redis;
import redis.embedded.RedisCluster;
import redis.embedded.core.PortProvider;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum JedisUtil {;

    // Bounded client timeouts: a server that never becomes ready should fail the test quickly instead of
    // hanging it until the CI job times out.
    public static final int TIMEOUT_MS = 2000;

    // Deadline for cluster/sentinel orchestration (discovery, failover state, ...) to settle
    public static final long READY_TIMEOUT_MS = 60_000L;

    /** Returns a port that was free a moment ago; avoids the fixed-port collisions between tests. */
    public static int newFreePort() {
        return PortProvider.newEphemeralPortProvider().get();
    }

    public static JedisPool newPool(final int port) {
        return new JedisPool(new JedisPoolConfig(), "localhost", port, TIMEOUT_MS);
    }

    public static Jedis newJedis(final int port) {
        return new Jedis("localhost", port, TIMEOUT_MS);
    }

    public static JedisSentinelPool newSentinelPool(final String masterName, final Set<String> sentinels) {
        return new JedisSentinelPool(masterName, sentinels, new JedisPoolConfig(), TIMEOUT_MS);
    }

    public static JedisCluster newCluster(final HostAndPort node) {
        return new JedisCluster(node, TIMEOUT_MS);
    }

    public static Set<String> jedisHosts(final Redis redis) {
        return portsToJedisHosts(redis.ports());
    }

    public static Set<String> sentinelHosts(final RedisCluster cluster) {
        return portsToJedisHosts(cluster.sentinelPorts());
    }

    public static Set<String> portsToJedisHosts(final List<Integer> ports) {
        final Set<String> hosts = new HashSet<>();
        for (final Integer p : ports) {
            hosts.add("localhost:" + p);
        }
        return hosts;
    }

    /**
     * Blocks until one of the sentinels has discovered the given master, or fails after the deadline.
     * Replaces the fixed sleeps that made sentinel-dependent tests flaky.
     */
    public static void awaitMasterDiscovery(final String masterName, final Set<String> sentinelHosts) {
        final long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (final String sentinel : sentinelHosts) {
                final String[] hostAndPort = sentinel.split(":");
                final String host = resolveLikeJedis(hostAndPort[0]);
                try (final Jedis jedis = new Jedis(host, Integer.parseInt(hostAndPort[1]), TIMEOUT_MS)) {
                    final List<String> master = jedis.sentinelGetMasterAddrByName(masterName);
                    if (master != null && !master.isEmpty()) return;
                } catch (final RuntimeException ignored) {
                    // The sentinel may not be accepting connections yet; retry until the deadline
                }
            }
            sleep(100L);
        }
        throw new AssertionError("Sentinels did not discover master '" + masterName + "' within "
            + READY_TIMEOUT_MS + " ms. Sentinels contacted: " + sentinelHosts);
    }

    /**
     * Mirrors Jedis' HostAndPort#convertHost: a loopback hostname resolves to 127.0.0.1, but the services under
     * test bind to getLocalHost() (on Debian/Ubuntu /etc/hosts the hostname maps to 127.0.1.1), and Jedis'
     * sentinel pool translates any loopback address to getLocalHost(). Connecting raw to "localhost" would
     * therefore target an address the sentinel does not listen on.
     */
    private static String resolveLikeJedis(final String host) {
        try {
            final InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || "0.0.0.0".equals(host) || host.startsWith("169.254")) {
                return InetAddress.getLocalHost().getHostAddress();
            }
        } catch (final UnknownHostException ignored) {
            // Fall through and use the host as-is
        }
        return host;
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for redis to become ready", e);
        }
    }

}
