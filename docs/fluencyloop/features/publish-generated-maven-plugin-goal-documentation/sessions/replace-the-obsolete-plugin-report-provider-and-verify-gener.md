# Session: replace the obsolete plugin report provider and verify generated docs

- **intent:** replace the obsolete plugin report provider and verify generated docs
- **started:** 2026-07-20

## Decision: Use the dedicated Maven Plugin Report Plugin for goal documentation

- **where:** `blastradius-maven-plugin/pom.xml reporting section`
- **why:** The Maven Plugin Plugin generates the descriptor and help mojo, but it has no report goal in the configured version. The dedicated report plugin generates the plugin overview and one page per goal.
- **alternative:** Keep the report binding on maven-plugin-plugin — rejected because Maven fails: version 3.13.1 does not provide a report goal.
- **design:** ../design.md#documentation-flow
- **constitution:** §II
- **trust:** ✓ verified

## Knowledge transfer

`helpmojo` belongs to the normal plugin build: it adds `blastradius:help` to the generated
plugin descriptor, so consumers can inspect goals and parameters without browsing source.
The staged Invoker fixture is the right integration test for this command because its
`@blastradius.version@` placeholder has already been resolved; the source fixture has not.

Generated HTML documentation is a separate Maven Site concern. The dedicated
`maven-plugin-report-plugin` reads the plugin metadata and writes
`target/site/plugin-info.html`, with one `*-mojo.html` page per goal. Keeping it in
`<reporting>` makes documentation available on demand without adding site generation to
ordinary `verify` builds.
