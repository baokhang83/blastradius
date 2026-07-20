def root = new File(basedir.toString())
def run = { List<String> command ->
    def process = new ProcessBuilder(command).directory(root).redirectErrorStream(true).start()
    def output = process.inputStream.getText("UTF-8")
    if (process.waitFor() != 0) {
        throw new IllegalStateException("command failed: ${command.join(' ')}\n${output}")
    }
    output
}

run(["git", "init", "--initial-branch=main"])
run(["git", "config", "user.email", "fixture@example.invalid"])
run(["git", "config", "user.name", "Blastradius fixture"])
run(["git", "add", "."])
run(["git", "commit", "-m", "baseline"])
run(["git", "tag", "baseline"])
def baselineCommit = run(["git", "rev-parse", "HEAD"]).trim()
run(["mvn", "-B", "--no-transfer-progress", "-Dblastradius.mode=track", "clean", "test"])

new File(root, "src/main/java/example/Foo.java").setText("""package example;

public class Foo {
    public int value() {
        return 1;
    }

    public int newBehavior() {
        return 99;
    }
}
""", "UTF-8")
new File(root, "src/test/java/example/NewTest.java").setText("""package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class NewTest {
    @Test
    void isAlwaysSelected() {
        assertEquals(1, new Foo().value());
    }
}
""", "UTF-8")
run(["git", "add", "src/main/java/example/Foo.java", "src/test/java/example/NewTest.java"])
run(["git", "commit", "-m", "change foo and add a test"])
assert run(["git", "rev-parse", "HEAD"]).trim() != baselineCommit : "fixture HEAD must differ from baseline"
assert run(["git", "rev-parse", "baseline"]).trim() == baselineCommit : "baseline tag must remain on the tracked commit"
