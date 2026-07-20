package io.github.baokhang83.blastradius.gradle;

import org.gradle.api.provider.Property;

/** Configuration shared by every Gradle {@code Test} task the plugin manages. */
public abstract class BlastradiusExtension {

    /** Git ref representing the tracked baseline. */
    public abstract Property<String> getBaseRef();

    /** Shared index path relative to the root project. */
    public abstract Property<String> getIndexPath();
}
