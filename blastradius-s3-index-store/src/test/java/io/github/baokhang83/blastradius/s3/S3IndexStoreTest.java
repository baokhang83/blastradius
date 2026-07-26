package io.github.baokhang83.blastradius.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class S3IndexStoreTest {

    @Test
    void roundTripsJsonBelowTheConfiguredPrefix() {
        InMemoryObjectStore objects = new InMemoryObjectStore();
        S3IndexStore<StoredIndex> store = new S3IndexStore<>(objects, "ci/indexes", StoredIndex.class);

        store.put(".blastradius/main/index.json", new StoredIndex("main"));

        assertEquals(new StoredIndex("main"), store.get(".blastradius/main/index.json").orElseThrow());
        assertEquals("ci/indexes/.blastradius/main/index.json", objects.values.keySet().iterator().next());
    }

    @Test
    void missingObjectIsAnAbsentIndex() {
        S3IndexStore<StoredIndex> store = new S3IndexStore<>(new InMemoryObjectStore(), "ci", StoredIndex.class);

        assertFalse(store.get(".blastradius/main/index.json").isPresent());
    }

    @Test
    void listsRelativeKeysBelowTheConfiguredPrefix() {
        S3IndexStore<StoredIndex> store = new S3IndexStore<>(new InMemoryObjectStore(), "ci", StoredIndex.class);
        store.put(".blastradius/one/index.json", new StoredIndex("one"));
        store.put(".blastradius/two/index.json", new StoredIndex("two"));

        assertEquals(List.of(".blastradius/one/index.json", ".blastradius/two/index.json"),
                store.keys(".blastradius"));
    }

    @Test
    void listsFromTheConfiguredStoreRootWhenThePrefixIsEmpty() {
        S3IndexStore<StoredIndex> store = new S3IndexStore<>(new InMemoryObjectStore(), "ci", StoredIndex.class);
        store.put("index.json", new StoredIndex("root"));

        assertEquals(List.of("index.json"), store.keys(""));
    }

    @Test
    void rejectsObjectKeysThatEscapeTheConfiguredPrefix() {
        S3IndexStore<StoredIndex> store = new S3IndexStore<>(new InMemoryObjectStore(), "ci", StoredIndex.class);

        assertThrows(IllegalArgumentException.class, () -> store.get("../other/index.json"));
        assertThrows(IllegalArgumentException.class, () -> store.put("/absolute/index.json", new StoredIndex("ignored")));
    }

    private record StoredIndex(String anchor) {}

    private static final class InMemoryObjectStore implements S3ObjectStore {
        private final Map<String, byte[]> values = new HashMap<>();

        @Override
        public Optional<byte[]> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public List<String> keys(String prefix) {
            return values.keySet().stream().filter(key -> key.startsWith(prefix)).sorted().toList();
        }

        @Override
        public void put(String key, byte[] value) {
            values.put(key, value);
        }
    }
}
