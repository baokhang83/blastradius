package io.github.baokhang83.blastradius.validator.mutation;

/** Opt-in, bounded mutation settings applied to every eligible historical pair in one run. */
public record MutationValidationConfig(
        String classFilter,
        int maxMutationClassesPerPair,
        int maxMutationsPerPair,
        long timeLimitMinutes) {

    public static final int DEFAULT_MAX_CLASSES_PER_PAIR = 10;
    public static final int DEFAULT_MAX_MUTATIONS_PER_PAIR = 20;
    public static final long DEFAULT_TIME_LIMIT_MINUTES = 60;

    public static MutationValidationConfig defaults() {
        return new MutationValidationConfig(
                null, DEFAULT_MAX_CLASSES_PER_PAIR, DEFAULT_MAX_MUTATIONS_PER_PAIR, DEFAULT_TIME_LIMIT_MINUTES);
    }

    public MutationValidationConfig {
        if (maxMutationClassesPerPair < 1 || maxMutationsPerPair < 1 || timeLimitMinutes < 1) {
            throw new IllegalArgumentException("mutation validation limits must be positive");
        }
    }
}
