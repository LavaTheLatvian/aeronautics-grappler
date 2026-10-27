package net.lavathelatvian.aeronautics_grappler;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(AeronauticsGrappler.MODID)
public class AeronauticsGrappler {

    public static final String MODID = "aeronautics_grappler";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<GrapplerItem> GRAPPLER = ITEMS.register("grappler",
            () -> new GrapplerItem(new Item.Properties().stacksTo(1).durability((int) Config.durability())));

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<PlungerProjectile>> PLUNGER_PROJECTILE =
            ENTITY_TYPES.register("plunger_projectile", () ->
                    EntityType.Builder.<PlungerProjectile>of(PlungerProjectile::new, MobCategory.MISC)
                            // Same settings as the Plunger Launcher's plunger in Simulated.
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.25F)
                            .clientTrackingRange(10)
                            .updateInterval(5)
                            .fireImmune()
                            .build(MODID + ":plunger_projectile"));

    public AeronauticsGrappler(IEventBus modEventBus, ModContainer modContainer) {

        modEventBus.addListener(this::commonSetup);

        ITEMS.register(modEventBus);

        // Show the grappler in Simulated's creative tab.
        SimulatedRegistrate.TAB_ITEMS.add(GRAPPLER::get);
        SimulatedRegistrate.ITEM_TO_SECTION.put(
                GRAPPLER.getId(),
                ResourceLocation.fromNamespaceAndPath("simulated", "simulated"));

        ENTITY_TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.register(this);

    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TooltipModifier.REGISTRY.register(GRAPPLER.get(),
                    new ItemDescription.Modifier(GRAPPLER.get(), FontHelper.Palette.STANDARD_CREATE));
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}