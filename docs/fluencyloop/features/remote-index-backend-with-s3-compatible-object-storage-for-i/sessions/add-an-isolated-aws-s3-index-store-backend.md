# Session: Add an isolated AWS S3 index-store backend

- **intent:** Add an isolated AWS S3 index-store backend
- **started:** 2026-07-25

## Knowledge transfer

- **S3IndexStore:** maps the existing typed index JSON to a caller-supplied, root-relative key under an optional bucket prefix. Missing objects are represented as an absent index; malformed JSON becomes an `UncheckedIOException` so existing applicability resolvers preserve full-suite fallback. **status:** documented.
- **AwsS3ObjectStore:** owns only AWS SDK byte reads and writes. A 404 is normal index absence, while authentication, transport, and other S3 errors are surfaced as unreadable storage rather than treated as valid data. **status:** documented.
- **S3IndexStoreFactory:** creates an SDK v2 client with the standard credential-provider chain. It uses an explicit endpoint and path-style access only for S3-compatible providers, and keeps credential values out of the plugin configuration. **status:** documented.
- **Dependency graph:** the new module keeps cloud dependencies out of the tracking agent. The JDK URL-connection HTTP client is selected explicitly and SLF4J is aligned in dependency management because the repository enforces dependency convergence. **status:** documented.

## Decision: isolated S3 transport behind the existing index-store contract

- **where:** `blastradius-s3-index-store`
- **why:** The cloud client stays outside the tracking-agent core while both build plugins can share one JSON and object-key implementation.
- **alternative:** Put AWS SDK calls in core or duplicate them per plugin — rejected: either burdens local agent users with cloud dependencies or lets plugin behavior drift.
- **design:** ../design.md#class-diagram
- **constitution:** §II
- **trust:** ✓ verified
