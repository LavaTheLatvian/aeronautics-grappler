package net.lavathelatvian.aeronautics_grappler;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PULL_STRENGTH = BUILDER
            .comment("How hard the grappler pulls. 1.0 = default, 2.0 = twice as strong, 0.5 = half.")
            .defineInRange("pullStrength", 1.0, 0.1, 5.0);

    public static final ModConfigSpec.DoubleValue PLUNGER_SPEED = BUILDER
            .comment("How fast the grappler's plunger flies. 1.0 = default, 2.0 = twice as strong, 0.5 = half.")
            .defineInRange("plungerSpeed", 1.0, 0.1, 5.0);

    public static final ModConfigSpec.DoubleValue MAX_RANGE = BUILDER
            .comment("How far the plunger can fly from where it was fired before it disappears, in blocks.")
            .defineInRange("maxRange", 64.0, 10.0, 200.0);

    public static final ModConfigSpec.DoubleValue COOLDOWN = BUILDER
            .comment("Cooldown between grappler uses, in ticks.")
            .defineInRange("cooldown", 60.0, 5.0, 1200.0);

    public static final ModConfigSpec.DoubleValue RETRACT_COOLDOWN = BUILDER
            .comment("Cooldown between the moment you fire the grappler and the moment you can retract its plunger, in ticks.")
            .defineInRange("maxRange", 20.0, 5.0, 1200.0);

    public static final ModConfigSpec.DoubleValue DURABILITY = BUILDER
            .comment("How much durability the grappler has. Also affects how much backtank pressure it uses.")
            .defineInRange("cooldown", 50.0, 1.0, 1000.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static double pullStrength() {
        return SPEC.isLoaded() ? PULL_STRENGTH.get() : 1.0;
    }

    public static double plungerSpeed() {
        return SPEC.isLoaded() ? PLUNGER_SPEED.get() : 1.0;
    }

    public static double maxRange() {
        return SPEC.isLoaded() ? MAX_RANGE.get() : 64.0;
    }

    public static double cooldown() {
        return SPEC.isLoaded() ? COOLDOWN.get() : 60.0;
    }

    public static double retractCooldown() {
        return SPEC.isLoaded() ? RETRACT_COOLDOWN.get() : 20.0;
    }

    public static double durability() {
        return SPEC.isLoaded() ? DURABILITY.get() : 50.0;
    }
}