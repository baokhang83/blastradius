# Test impact analysis for Maven and JUnit 5

Blastradius is an open-source Maven plugin that selects JUnit 5 tests potentially affected by
changes to Java and conventional Kotlin source files. It records observed class dependencies
on a base-branch build and uses them to narrow later pull request builds.

## Requirements and installation

Run Maven with JDK 21 or later in a Git repository. The plugin is published on
[Maven Central](https://central.sonatype.com/artifact/io.github.baokhang83.blastradius/blastradius-maven-plugin).
Add the following to the project's `<build><plugins>` section:

```xml
<plugin>
  <groupId>io.github.baokhang83.blastradius</groupId>
  <artifactId>blastradius-maven-plugin</artifactId>
  <version>0.4.0</version>
  <executions>
    <execution>
      <phase>process-test-classes</phase>
      <goals><goal>select</goal></goals>
    </execution>
  </executions>
  <configuration>
    <baseRef>main</baseRef>
  </configuration>
</plugin>
```

Set `baseRef` to a Git reference available in the checkout. Run `mvn verify` as usual. A
base-reference run tracks the suite and saves an index under `.blastradius/`; a change-branch
run uses a suitable saved index to select tests. The first run without a usable index runs the
full suite. The [plugin reference](../blastradius-maven-plugin/README.md) describes the modes,
configuration, and output.

## Using it in CI

Persist `.blastradius/` after a successful base-branch run and restore it before running tests
on a pull request. Ensure the checkout has the base reference and enough Git history to find
the merge base. If runners cannot share a cache, an S3-compatible index store is optional.
See the [GitHub Actions cache example](../README.md#github-actions-cache-example) and
[S3 configuration](../blastradius-maven-plugin/README.md#shared-s3-index-store).

New and modified tests are included. Missing or incompatible indexes and changes that cannot
be narrowed safely can cause a full run. Selection does not guarantee that every affected
test is identified: retain a regular full-suite run and review the
[known limitations](../README.md#known-limitations) and
[historical validation](../HISTORICAL_REPLAY_ANALYSIS.md).
