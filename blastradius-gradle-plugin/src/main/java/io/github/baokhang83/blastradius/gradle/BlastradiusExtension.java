package io.github.baokhang83.blastradius.gradle;

import org.gradle.api.provider.Property;

/** Configuration shared by every Gradle {@code Test} task the plugin manages. */
public abstract class BlastradiusExtension {

    /** Git ref representing the tracked baseline. */
    public abstract Property<String> getBaseRef();

    /** Root-relative index-file template; each resolved commit receives its own key. */
    public abstract Property<String> getIndexPath();
}
