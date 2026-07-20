# Design: publish generated Maven plugin goal documentation

started: 2026-07-20

## Documentation shape

```mermaid
classDiagram
  class PluginAnnotations {
    @Mojo select
    @Parameter configuration
  }
  class MavenPluginPlugin {
    descriptor
    helpmojo
  }
  class MavenPluginReportPlugin {
    report
  }
  class PluginDescriptor {
    plugin.xml
    help goal
  }
  class ConsumerMaven {
    mvn blastradius:help
  }
  class PluginSite {
    plugin-info.html
  }

  PluginAnnotations --> MavenPluginPlugin : processed by
  MavenPluginPlugin --> PluginDescriptor : generates
  PluginDescriptor --> ConsumerMaven : resolves help goal
  MavenPluginReportPlugin --> PluginSite : reports goal metadata
```

## Documentation flow

```mermaid
sequenceDiagram
  participant Build as Maven plugin build
  participant BuildTools as maven-plugin-plugin
  participant ReportTools as maven-plugin-report-plugin
  participant Consumer as Consumer Maven
  participant Site as Maven Site

  Build->>BuildTools: descriptor and helpmojo
  BuildTools-->>Build: plugin.xml with select and help
  Consumer->>Build: mvn blastradius:help
  Build-->>Consumer: goals and parameters
  Site->>ReportTools: report
  ReportTools-->>Site: target/site/plugin-info.html
```

## Key decision

Keep descriptor and `helpmojo` generation in the plugin build, while using the reporting lifecycle
for `plugin-info.html`. This exposes help to consumers and produces the site on demand without
making normal `verify` builds pay for site generation.
