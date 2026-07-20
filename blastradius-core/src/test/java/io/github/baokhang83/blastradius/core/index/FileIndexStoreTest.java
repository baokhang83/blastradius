package io.github.baokhang83.blastradius.core.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileIndexStoreTest {

    @TempDir
    Path rootDirectory;

    @Test
    void missingKeyReturnsEmpty() {
        IndexStore<StoredIndex> store = new FileIndexStore<>(rootDirectory, StoredIndex.class);

        assertTrue(store.get(".blastradius/index.json").isEmpty());
    }

    @Test
    void writesAndReadsValueAtKeyCreatingParentDirectories() throws Exception {
        IndexStore<StoredIndex> store = new FileIndexStore<>(rootDirectory, StoredIndex.class);
        StoredIndex index = new StoredIndex("abc123", List.of("example.Foo", "example.Bar"));

        store.put(".blastradius/index.json", index);

        assertTrue(Files.isRegularFile(rootDirectory.resolve(".blastradius/index.json")));
        assertEquals(index, store.get(".blastradius/index.json").orElseThrow());
    }

    @Test
    void rejectsKeyThatEscapesConfiguredRoot() {
        IndexStore<StoredIndex> store = new FileIndexStore<>(rootDirectory, StoredIndex.class);

        assertThrows(IllegalArgumentException.class, () -> store.put("../index.json", new StoredIndex("abc123", List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.put(rootDirectory.resolve(".blastradius/index.json").toString(), new StoredIndex("abc123", List.of())));
        assertFalse(Files.exists(rootDirectory.getParent().resolve("index.json")));
    }

    record StoredIndex(String anchorCommit, List<String> classNames) {}
}
