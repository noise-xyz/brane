package io.brane.smoke;

import io.brane.core.abi.AbiEncoder;

public class SmokeApp {
    public static void main(String[] args) {
        System.out.println("=== Brane Smoke Test ===");
        System.out.println("Attempting to load AbiEncoder...");
        
        try {
            String className = AbiEncoder.class.getName();
            System.out.println("✅ Successfully loaded: " + className);
            System.out.println("Smoke Test Passed!");
        } catch (Throwable t) {
            System.err.println("❌ Failed to load AbiEncoder");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
