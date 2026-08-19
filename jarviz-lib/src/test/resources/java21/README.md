# Java 21 test fixture

`java21-fixture-1.0.0.jar` contains the classes under `src` compiled with a JDK 21 compiler, so every
class file in it has major version 65 (Java 21). It is used by `Java21BytecodeAnalysisTest` to verify
that Jarviz can analyze Java 21 bytecode.

The jar is checked in as a binary because the Maven build compiles the project with `source`/`target`
1.8 and the CI build runs on JDK 8 and 11, neither of which can produce Java 21 class files.

To regenerate the jar (requires a JDK 21 or later):

```bash
cd jarviz-lib/src/test/resources/java21
javac --release 21 -d /tmp/java21-fixture src/com/vrbo/jarviz/java21/*.java
jar --create --file java21-fixture-1.0.0.jar -C /tmp/java21-fixture .
```

The file name follows the `<artifactId>-<version>.jar` convention used by
`MavenArtifactDiscoveryService` so that the test can resolve it as a regular Jarviz artifact.
