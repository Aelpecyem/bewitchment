package moriyashiine.bewitchment.common.entity.living;

import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.api.interfaces.MasterAccessor;
import moriyashiine.bewitchment.common.entity.living.util.BWHostileEntity;
import moriyashiine.bewitchment.common.registry.BWMaterials;
import moriyashiine.bewitchment.common.registry.BWStatusEffects;
import moriyashiine.bewitchment.mixin.StatusEffectAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BaphometEntity extends BWHostileEntity {
	private final ServerBossBar bossBar;
	
	public int flameIndex = random.nextInt(8);
	
	public BaphometEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		bossBar = new ServerBossBar(getDisplayName(), BossBar.Color.RED, BossBar.Style.PROGRESS);
		setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0);
		setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0);
		experiencePoints = 50;
	}
	
	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 375).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8).add(EntityAttributes.GENERIC_ARMOR, 6).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.75);
	}
	
	@Override
	protected boolean hasShiny() {
		return false;
	}
	
	@Override
	public int getVariants() {
		return 1;
	}
	
	@Override
	public EntityGroup getGroup() {
		return BewitchmentAPI.DEMON;
	}
	
	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_BLAZE_AMBIENT;
	}
	
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ZOMBIE_HURT;
	}
	
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_BLAZE_DEATH;
	}
	
	@Override
	protected float getSoundPitch() {
		return 0.5f;
	}
	
	@Override
	public boolean canBeLeashedBy(PlayerEntity player) {
		return false;
	}
	
	@Override
	public boolean canHaveStatusEffect(StatusEffectInstance effect) {
		return ((StatusEffectAccessor) effect.getEffectType()).bw_getType() == StatusEffectType.BENEFICIAL;
	}
	
	@Override
	public boolean isAffectedBySplashPotions() {
		return false;
	}
	
	@Override
	public void tick() {
		super.tick();
		flameIndex = ++flameIndex % 8;
		if (!world.isClient) {
			bossBar.setPercent(getHealth() / getMaxHealth());
			LivingEntity target = getTarget();
			int timer = age + getEntityId();
			if (timer % 20 == 0) {
				heal(1);
			}
			if (target != null) {
				lookAtEntity(target, 360, 360);
				if (timer % 60 == 0) {
					for (int i = -1; i <= 1; i++) {
						FireballEntity fireball = new FireballEntity(world, this, target.getX() - getX() + i, target.getBodyY(0.5) - getBodyY(0.5), target.getZ() - getZ() + i);
						fireball.updatePosition(fireball.getX(), getBodyY(0.5), fireball.getZ());
						world.playSound(null, getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1, 1);
						world.spawnEntity(fireball);
					}
					swingHand(Hand.MAIN_HAND);
				}
				if (timer % 600 == 0 && world.getEntitiesByType(EntityType.BLAZE, new Box(getBlockPos()).expand(32), entity -> ((MasterAccessor) entity).getMasterUUID().equals(getUuid())).size() < 3) {
					summonMinions();
				}
			}
		}
	}
	
	@Override
	public boolean tryAttack(Entity target) {
		boolean flag = super.tryAttack(target);
		if (flag && target instanceof LivingEntity) {
			target.setOnFireFor(8);
			target.addVelocity(0, 0.2, 0);
			swingHand(Hand.MAIN_HAND);
		}
		return flag;
	}
	
	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target != null) {
			if (target instanceof MasterAccessor && getUuid().equals(((MasterAccessor) target).getMasterUUID())) {
				return;
			}
			if (world.getEntitiesByType(EntityType.BLAZE, new Box(getBlockPos()).expand(32), entity -> !entity.removed && ((MasterAccessor) entity).getMasterUUID().equals(getUuid())).size() < 3) {
				summonMinions();
			}
		}
		super.setTarget(target);
	}
	
	@Override
	public void setCustomName(@Nullable Text name) {
		super.setCustomName(name);
		bossBar.setName(getDisplayName());
	}
	
	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		bossBar.addPlayer(player);
	}
	
	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}
	
	@Override
	public void readCustomDataFromTag(CompoundTag tag) {
		super.readCustomDataFromTag(tag);
		if (hasCustomName()) {
			bossBar.setName(getDisplayName());
		}
	}
	
	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new MeleeAttackGoal(this, 1, true));
		goalSelector.add(2, new WanderAroundFarGoal(this, 1));
		goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8));
		goalSelector.add(3, new LookAroundGoal(this));
		targetSelector.add(0, new RevengeGoal(this));
		targetSelector.add(1, new FollowTargetGoal<>(this, LivingEntity.class, 10, true, false, entity -> entity.getGroup() != BewitchmentAPI.DEMON && BewitchmentAPI.getArmorPieces(entity, stack -> stack.getItem() instanceof ArmorItem && ((ArmorItem) stack.getItem()).getMaterial() == BWMaterials.BESMIRCHED_ARMOR) < 3));
	}
	
	private void summonMinions() {
		if (!world.isClient) {
			for (int i = 0; i < 3; i++) {
				BlazeEntity blaze = EntityType.BLAZE.create(world);
				if (blaze != null) {
					BewitchmentAPI.attemptTeleport(blaze, getBlockPos(), 3);
					blaze.pitch = random.nextInt(360);
					blaze.setTarget(getTarget());
					((MasterAccessor) blaze).setMasterUUID(getUuid());
					blaze.setPersistent();
					blaze.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE));
					blaze.addStatusEffect(new StatusEffectInstance(BWStatusEffects.MAGIC_RESISTANCE, Integer.MAX_VALUE));
					blaze.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, Integer.MAX_VALUE));
					blaze.addStatusEffect(new StatusEffectInstance(BWStatusEffects.HARDENING, Integer.MAX_VALUE, 1));
					world.spawnEntity(blaze);
				}
			}
		}
	}
}