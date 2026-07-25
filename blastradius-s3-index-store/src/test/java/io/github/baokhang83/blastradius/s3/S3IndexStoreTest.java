package io.github.baokhang83.blastradius.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
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
        public void put(String key, byte[] value) {
            values.put(key, value);
        }
    }
}
