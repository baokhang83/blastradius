package io.github.baokhang83.blastradius.gradle;

import org.gradle.api.provider.Property;

/** Configuration shared by every Gradle {@code Test} task the plugin manages. */
public abstract class BlastradiusExtension {

    /** Git ref representing the tracked baseline. */
    public abstract Property<String> getBaseRef();

    /** Root-relative index-file template; each resolved commit receives its own key. */
    public abstract Property<String> getIndexPath();

    /** Controls the enabled-by-default one-hop direct-invocation fallback in format-3 indexes. */
    public abstract Property<Boolean> getDirectInvocationFallback();

    /** Selects the local file store or the shared S3-compatible store. */
    public abstract Property<String> getIndexStore();

    public abstract Property<String> getS3Bucket();

    public abstract Property<String> getS3Prefix();

    public abstract Property<String> getS3Region();

    public abstract Property<String> getS3Endpoint();
}
