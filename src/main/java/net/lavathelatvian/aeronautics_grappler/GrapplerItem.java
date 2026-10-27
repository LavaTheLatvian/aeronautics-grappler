package net.lavathelatvian.aeronautics_grappler;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetItemMethods;
import com.simibubi.create.foundation.item.CustomArmPoseItem;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class GrapplerItem extends Item implements CustomArmPoseItem {

    public static final int MAX_USES = 50;

    private static final double AIM_RANGE = 32.0;
    private static final double PROJECTILE_SPEED = 1.8;
    private static final int COOLDOWN_TICKS = (int) Config.retractCooldown();
    public static final int RETRACT_COOLDOWN_TICKS = (int) Config.cooldown();
    // where barrel is
    public static final Vec3 BARREL_OFFSET = new Vec3(0.45, -0.25, 0.25);

    public GrapplerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Holding one in each hand: let the other hand's item handle the click.
        if (ShootableGadgetItemMethods.shouldSwap(player, stack, hand, s -> s.getItem() instanceof GrapplerItem)) {
            return InteractionResultHolder.fail(stack);
        }

        if (level instanceof ServerLevel serverLevel) {
            // Right-click while a plunger is out (flying or stuck): reel it in instead of firing.
            if (removeOldPlungers(serverLevel, player)) {
                serverLevel.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                        SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                ShootableGadgetItemMethods.applyCooldown(player, stack, hand,
                        s -> s.getItem() instanceof GrapplerItem, RETRACT_COOLDOWN_TICKS);
                return InteractionResultHolder.success(stack);
            }

            Vec3 barrel = getBarrelPos(player, hand);
            Vec3 motion = getAimedMotion(player, barrel);

            PlungerProjectile plunger = new PlungerProjectile(level, player);
            plunger.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.5F, 0.0F);
            plunger.setPos(barrel);
            plunger.setOldPosAndRot();
            plunger.setDeltaMovement(motion);
            plunger.setFiredFromMainHand(hand == InteractionHand.MAIN_HAND); // decides which side the rope leaves from
            level.addFreshEntity(plunger);

            // Placeholder sound until the launcher's own sound event is hooked up.
            level.playSound(null, barrel.x, barrel.y, barrel.z,
                    SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);

            ShootableGadgetItemMethods.applyCooldown(player, stack, hand,
                    s -> s.getItem() instanceof GrapplerItem, COOLDOWN_TICKS);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));

            if (!BacktankUtil.canAbsorbDamage(player, MAX_USES)) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }

        return InteractionResultHolder.success(stack);
    }

    public static void applyReloadCooldown(Player player) {
        player.getCooldowns().addCooldown(AeronauticsGrappler.GRAPPLER.get(), RETRACT_COOLDOWN_TICKS);
    }

    private static boolean removeOldPlungers(ServerLevel level, Player player) {
        boolean removed = false;
        for (PlungerProjectile old : level.getEntities(AeronauticsGrappler.PLUNGER_PROJECTILE.get(),
                plunger -> plunger.getOwner() == player)) {
            old.discard();
            removed = true;
        }
        return removed;
    }

    private static Vec3 getBarrelPos(Player player, InteractionHand hand) {
        Level level = player.level();

        Vec3 barrel = ShootableGadgetItemMethods.getGunBarrelVec(
                player, hand == InteractionHand.MAIN_HAND, BARREL_OFFSET);

        barrel = level.clip(new ClipContext(
                player.getEyePosition(), barrel,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getLocation();

        return Sable.HELPER.projectOutOfSubLevel(level, barrel);
    }

    private static Vec3 getAimedMotion(Player player, Vec3 barrel) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        BlockHitResult hit = level.clip(new ClipContext(
                eye, eye.add(look.scale(AIM_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.MISS) {
            return look.scale(PROJECTILE_SPEED).scale(Config.plungerSpeed());
        }

        Vec3 target = Sable.HELPER.projectOutOfSubLevel(level, hit.getLocation());
        return target.subtract(barrel).normalize().scale(PROJECTILE_SPEED);
    }


    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    // no arm swing when firing
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    // Client-only
    @OnlyIn(Dist.CLIENT)
    @Override
    public HumanoidModel.ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand) {
        return HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BacktankUtil.isBarVisible(stack, MAX_USES);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return BacktankUtil.getBarWidth(stack, MAX_USES);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BacktankUtil.getBarColor(stack, MAX_USES);
    }
}