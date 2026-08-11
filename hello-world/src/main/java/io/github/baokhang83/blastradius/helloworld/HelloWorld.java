package io.github.baokhang83.blastradius.helloworld;

/** Minimal FluencyLoop demo entry point. No product code. */
public final class HelloWorld {

    private HelloWorld() {
    }

    public static String greeting() {
        return "Hello, World!";
    }

    public static void main(String[] args) {
        System.out.println(greeting());
    }
}
