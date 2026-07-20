def root = new File(basedir.toString())
def run = { List<String> command ->
    def process = new ProcessBuilder(command).directory(root).redirectErrorStream(true).start()
    def output = process.inputStream.getText("UTF-8")
    if (process.waitFor() != 0) {
        throw new IllegalStateException("command failed: ${command.join(' ')}\n${output}")
    }
}

run(["git", "init", "--initial-branch=main"])
run(["git", "config", "user.email", "fixture@example.invalid"])
run(["git", "config", "user.name", "Blastradius fixture"])
run(["git", "add", "."])
run(["git", "commit", "-m", "baseline"])
run(["git", "tag", "baseline"])
