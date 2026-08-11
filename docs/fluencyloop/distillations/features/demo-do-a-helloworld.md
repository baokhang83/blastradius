# demo-do-a-helloworld

**Before:** No throwaway or example module existed in the reactor; every module carried
production responsibilities (tracking, selection, plugin, or validator code).

**After:** A minimal `hello-world` module exists purely to exercise tooling and workflows
against this repository. It exposes one class, `HelloWorld`, with a fixed greeting and a
runnable `main`. It is wired into the root reactor's module list but inherits
`maven.deploy.skip=true` from the parent POM, so it builds and tests like any other module
without ever being published to Maven Central.

No other product behavior changed.
