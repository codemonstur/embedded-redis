[![GitHub Release](https://img.shields.io/github/release/codemonstur/embedded-redis.svg)](https://github.com/codemonstur/embedded-redis/releases)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.codemonstur/embedded-redis/badge.svg)](http://mvnrepository.com/artifact/com.github.codemonstur/embedded-redis)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

embedded-redis
==============

Redis embedded server for Java integration testing.

Forked from [ozimov](https://github.com/ozimov/embedded-redis),
which was forked from [kstyrc](https://github.com/kstyrc/embedded-redis)

Maven dependency
==============

Maven Central:
```xml
<dependency>
  <groupId>com.github.codemonstur</groupId>
  <artifactId>embedded-redis</artifactId>
  <version>0.13.0</version>
</dependency>
```

Usage
==============

Running RedisServer is as simple as:
```java
RedisServer redisServer = new RedisServer(6379);
redisServer.start();
// do some work
redisServer.stop();
```

You can also provide RedisServer with your own executable:
```java
// 1) given explicit file (os-independence broken!)
RedisServer redisServer = new RedisServer("/path/to/your/redis", 6379);

// 2) given os-independent matrix
ExecutableProvider customProvider = new ExecutableProviderBuilder()
    .put(OS.UNIX, "/path/to/unix/redis")
    .put(OS.WINDOWS, Architecture.x86, "/path/to/windows/redis")
    .put(OS.WINDOWS, Architecture.x86_64, "/path/to/windows/redis")
    .put(OS.MAC_OS_X, Architecture.x86, "/path/to/macosx/redis")
    .put(OS.MAC_OS_X, Architecture.x86_64, "/path/to/macosx/redis")
  
RedisServer redisServer = new RedisServer(6379, customProvider);
```

You can also use fluent API to create RedisServer:
```java
RedisServer redisServer = RedisServer.builder()
  .executableProvider(customRedisProvider)
  .port(6379)
  .slaveOf("locahost", 6378)
  .configFile("/path/to/your/redis.conf")
  .build();
```

Or even create simple redis.conf file from scratch:
```java
RedisServer redisServer = RedisServer.builder()
  .executableProvider(customRedisProvider)
  .port(6379)
  .setting("bind 127.0.0.1") // good for local development on Windows to prevent security popups
  .slaveOf("locahost", 6378)
  .setting("daemonize no")
  .setting("appendonly no")
  .setting("maxmemory 128M")
  .build();
```

## Using ARM hardware

The library contains a pre-compiled binary for ARM architecture.
However, this binary was generated using the QEMU emulator and did not work with the Redis tests.
It is not known whether it will actually work.

The makefile contains a target (build-arm) that will generate a binary from Redis source.
Use this to generate your own binary and then configure it.

```java
final ExecutableProvider executables = new ExecutableProviderBuilder()
    .addProvidedVersions()
    .put(OS.UNIX, Architecture.aarch64, "/path/to/resource/redis-server-arm")
    .build();

new RedisServer(Redis.DEFAULT_REDIS_PORT, executables);
```

## Setting up a cluster

Our Embedded Redis has support for HA Redis clusters with Sentinels and master-slave replication

#### Using ephemeral ports
A simple redis integration test with Redis cluster on ephemeral ports, with setup similar to that from production would look like this:
```java
public class SomeIntegrationTestThatRequiresRedis {
  private RedisCluster cluster;
  private Set<String> jedisSentinelHosts;

  @Before
  public void setup() throws Exception {
    //creates a cluster with 3 sentinels, quorum size of 2 and 3 replication groups, each with one master and one slave
    cluster = RedisCluster.builder().ephemeral().sentinelCount(3).quorumSize(2)
                    .replicationGroup("master1", 1)
                    .replicationGroup("master2", 1)
                    .replicationGroup("master3", 1)
                    .build();
    cluster.start();

    //retrieve ports on which sentinels have been started, using a simple Jedis utility class
    jedisSentinelHosts = JedisUtil.sentinelHosts(cluster);
  }
  
  @TestApp
  public void test() throws Exception {
    // testing code that requires redis running
    JedisSentinelPool pool = new JedisSentinelPool("master1", jedisSentinelHosts);
  }
  
  @After
  public void tearDown() throws Exception {
    cluster.stop();
  }
}
```

#### Retrieving ports
The above example starts Redis cluster on ephemeral ports, which you can later get with ```cluster.ports()```,
which will return a list of all ports of the cluster. You can also get ports of sentinels with ```cluster.sentinelPorts()```
or servers with ```cluster.serverPorts()```. ```JedisUtil``` class contains utility methods for use with Jedis client.

#### Using predefined ports
You can also start Redis cluster on predefined ports and even mix both approaches:
```java
public class SomeIntegrationTestThatRequiresRedis {
  private RedisCluster cluster;

  @Before
  public void setup() throws Exception {
    final List<Integer> sentinels = Arrays.asList(26739, 26912);
    final List<Integer> group1 = Arrays.asList(6667, 6668);
    final List<Integer> group2 = Arrays.asList(6387, 6379);
    //creates a cluster with 2 sentinels, quorum size of 2 and 3 replication groups, each with one master and one slave
    cluster = RedisCluster.builder().sentinelPorts(sentinels).quorumSize(2)
                    .serverPorts(group1).replicationGroup("master1", 1)
                    .serverPorts(group2).replicationGroup("master2", 1)
                    .ephemeralServers().replicationGroup("master3", 1)
                    .build();
    cluster.start();
  }
//(...)
```
The above will create and start a cluster with sentinels on ports ```26739, 26912```, first replication group on ```6667, 6668```,
second replication group on ```6387, 6379``` and third replication group on ephemeral ports.

Redis version
==============

When not provided with the desired redis executable, RedisServer runs os-dependent executable enclosed in jar. Currently is uses:
- Redis 6.2.6-v5 in case of Linux/Unix x64 or arm64 ([x64 source](https://packages.redis.io/redis-stack/redis-stack-server-6.2.6-v5.jammy.x86_64.tar.gz), [arm64 source](https://packages.redis.io/redis-stack/redis-stack-server-6.2.6-v5.jammy.arm64.tar.gz))
- Redis 6.2.7 in case of Linux/Unix x86 ([source](https://github.com/signalapp/embedded-redis/tree/2aee2439c3314dba5d03a09dda1897d891f774b3/src/main/resources))
- Redis 6.2.6-v5 in case of OSX x64 or arm64 ([x64 source](https://packages.redis.io/redis-stack/redis-stack-server-6.2.6-v5.catalina.x86_64.zip), [arm64 source](https://packages.redis.io/redis-stack/redis-stack-server-6.2.6-v5.monterey.arm64.zip))
- Redis 5.0.14.1 in case of Windows x64 ([source](https://github.com/tporadowski/redis/releases/tag/v5.0.14.1))

However, you should provide RedisServer with redis executable if you need specific version.


License
==============
Licensed under the Apache License, Version 2.0


Contributors
==============
 * Krzysztof Styrc ([@kstyrc](http://github.com/kstyrc))
 * Piotr Turek ([@turu](http://github.com/turu))
 * anthonyu ([@anthonyu](http://github.com/anthonyu))
 * Artem Orobets ([@enisher](http://github.com/enisher))
 * Sean Simonsen ([@SeanSimonsen](http://github.com/SeanSimonsen))
 * Rob Winch ([@rwinch](http://github.com/rwinch))
