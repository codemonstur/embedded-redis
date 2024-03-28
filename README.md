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
  <version>1.4.3</version>
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
RedisServer redisServer = new RedisServer(6379, new File("/path/to/your/redis"));
```

You can also use fluent API to create RedisServer:
```java
RedisServer redisServer = RedisServer.newRedisServer()
  .executableProvider(customRedisProvider)
  .port(6379)
  .slaveOf("locahost", 6378)
  .configFile("/path/to/your/redis.conf")
  .build();
```

Or even create simple redis.conf file from scratch:
```java
RedisServer redisServer = RedisServer.newRedisServer()
  .executableProvider(customRedisProvider)
  .port(6379)
  .setting("bind 127.0.0.1") // good for local development on Windows to prevent security popups
  .slaveOf("locahost", 6378)
  .setting("daemonize no")
  .setting("appendonly no")
  .setting("maxmemory 128M")
  .build();
```

## Binaries

Redis binaries are included in the library by default.

When no `ExecutableProvider` is given the code will attempt to discover which OS and Architecture is being used and choose an appropriate binary.

Not all operating systems and architectures are supported.
If you find that the default binaries do not work your best approach is to compile your own and configure an `ExecutableProvider`.

Additional binaries that are not part of the default set are located in `src/main/binaries` in this project.
You can use the `ExecutableProvider.newCachedUrlProvider()` to make use of them. (currently only 3 binaries)
Example code how to do this can be found at [src/test/java/tools/DownloadUriTest.java](src/test/java/tools/DownloadUriTest.java).

## SSL/TLS Troubleshooting

You might get an error when you try to start the default binary without having openssl installed. The default
binaries have TLS support but require a library on the host OS. On MacOS you will probably get an error that
looks like this:

    '/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
    '/System/Volumes/Preboot/Cryptexes/OS/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
    '/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
    '/usr/lib/libssl.3.dylib' (no such file, not in dyld cache)

One option for resolving the issue is to install openssl using `brew install openssl@3`. Alternatively, you
can use a binary that doesn't have TLS support. Either by compiling your own from source, or by using HankCP's
binary at ExecutableProvider.REDIS_7_2_MACOSX_14_SONOMA_HANKCP, or downloading one from some other place.

On linux the error will look like this:

    /app/redis-server-6.2.6-v5-linux-amd64: error while loading shared libraries: libssl.so.3: cannot open
    shared object file: No such file or directory

The problem is the same as on MacOS. You need a binary that doesn't require the libssl library or you need to
provide that library. If you are running the app on your host you can install the needed package using your 
package manager. Such as with apt-get (sudo apt-get install openssl). If you are running this 
inside a docker image you'll need to make sure the library is available inside the image.

## Setting up a cluster

Our Embedded Redis has support for:
- HA Redis clusters with Sentinels and master-slave replication
- Sharded Redis clusters with node replication

#### Using ephemeral ports
A simple redis integration test with Redis cluster on ephemeral ports, with setup similar to that from production would look like this:
```java
public class SomeIntegrationTestThatRequiresRedis {
  private RedisCluster cluster;

  @Before
  public void setup() throws Exception {
    String bindAddress = Inet4Address.getLocalHost().getHostAddress();
    RedisSentinelBuilder sentinelBuilder = RedisSentinel.newRedisSentinel();
    sentinelBuilder.bind(bindAddress);

    //creates a cluster with 3 sentinels, quorum size of 2 and 3 replication groups, each with one master and one slave
    cluster =
            RedisCluster.newRedisCluster()
                    .withSentinelBuilder(sentinelBuilder)
                    .ephemeralServers()
                    .sentinelStartingPort(26400)
                    .sentinelCount(3)
                    .quorumSize(2)
                    .replicationGroup("master1", 1)
                    .replicationGroup("master2", 1)
                    .replicationGroup("master3", 1)
                    .build();
    cluster.start();
  }
  
  @Test
  public void test() throws Exception {
    // testing code that requires redis running
    JedisSentinelPool pool = new JedisSentinelPool("master1", Set.of("localhost:26400", "localhost:26401", "localhost:26402"));
  }
  
  @After
  public void tearDown() throws Exception {
    cluster.stop();
  }
}
```

For an example of setting up a sharded redis cluster check out the code in `RedisShardedServerClusterTest`.

#### Retrieving ports
The above example starts Redis cluster with servers on ephemeral ports and sentinels on ports 26400, 26401 and 26402. You can later get ports of servers with ```cluster.serverPorts()```, sentinels with ```cluster.sentinelPorts()``` or all ports with ```cluster.ports()```.

Redis version
==============

When not provided with the desired redis executable, RedisServer runs os-dependent executable enclosed in jar. Currently it uses:
- Redis 6.2.7 in case of Linux/Unix x86 or arm64 ([x86 source](https://github.com/signalapp/embedded-redis/blob/2aee2439c3314dba5d03a09dda1897d891f774b3/src/main/resources/redis-server-6.2.7-linux-386), [arm64 source](https://github.com/signalapp/embedded-redis/blob/2aee2439c3314dba5d03a09dda1897d891f774b3/src/main/resources/redis-server-6.2.7-linux-arm64))
- Redis 6.2.6-v5 in case of Linux/Unix x64 ([source](https://packages.redis.io/redis-stack/redis-stack-server-6.2.6-v5.jammy.x86_64.tar.gz))
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
 * Cristian Badila ([@cristi-badila](http://github.com/cristi-badila))
