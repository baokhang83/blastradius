package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TestIdentityTest {

    @Test
    void sameClassAndMethodAreEqual() {
        TestIdentity a = new TestIdentity("com.example.FooTest", "bar");
        TestIdentity b = new TestIdentity("com.example.FooTest", "bar");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentMethodNamesAreNotEqual() {
        TestIdentity a = new TestIdentity("com.example.FooTest", "bar");
        TestIdentity b = new TestIdentity("com.example.FooTest", "baz");
        assertNotEquals(a, b);
    }

    @Test
    void differentClassNamesAreNotEqual() {
        TestIdentity a = new TestIdentity("com.example.FooTest", "bar");
        TestIdentity b = new TestIdentity("com.example.OtherTest", "bar");
        assertNotEquals(a, b);
    }

    @Test
    void methodNameMayBeNullForClassLevelIdentity() {
        TestIdentity a = new TestIdentity("com.example.FooTest", null);
        assertNull(a.methodName());
        assertEquals("com.example.FooTest", a.className());
    }

    @Test
    void classNameMustNotBeNull() {
        assertThrows(NullPointerException.class, () -> new TestIdentity(null, "bar"));
    }

    @Test
    void baselineKeyStripsParameterizedInvocationSuffix() {
        TestIdentity invocation = new TestIdentity("com.example.FooTest", "checksValue(int)[1]");
        assertEquals(new TestIdentity("com.example.FooTest", "checksValue"), invocation.baselineKey());
    }

    @Test
    void baselineKeyStripsBareBracketedSuffixWithNoParameterTypes() {
        TestIdentity invocation = new TestIdentity("com.example.FooTest", "checksValue[2]");
        assertEquals(new TestIdentity("com.example.FooTest", "checksValue"), invocation.baselineKey());
    }

    @Test
    void baselineKeyIsUnchangedForAnOrdinaryTestMethod() {
        TestIdentity plain = new TestIdentity("com.example.FooTest", "checksValue");
        assertEquals(plain, plain.baselineKey());
    }

    @Test
    void baselineKeyIsUnchangedForClassLevelIdentity() {
        TestIdentity classLevel = new TestIdentity("com.example.FooTest", null);
        assertEquals(classLevel, classLevel.baselineKey());
    }
}
