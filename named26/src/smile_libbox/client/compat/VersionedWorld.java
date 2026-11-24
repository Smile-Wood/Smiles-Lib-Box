package smile_libbox.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import smile_libbox.api.world.EntityView;
import smile_libbox.api.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.2（官方名命名空间）的 {@link World.Backend} 实现。
 *
 * <p>与 intermediary 版逻辑一致，差异集中在几处 26.x 的重构上：</p>
 * <ul>
 *   <li>{@code handleInventoryMouseClick} → {@code handleContainerInput}，
 *       {@code ClickType} → {@code ContainerInput}；</li>
 *   <li>当前界面由 {@code Gui} 管理，不再有 {@code Minecraft#screen} 字段；</li>
 *   <li>{@code Level#getEntitiesOfClass} 移除，改用 {@code getEntities(except, aabb, predicate)}；</li>
 *   <li>{@code ResourceLocation} 命名为 {@code Identifier}。</li>
 * </ul>
 */
public final class VersionedWorld implements World.Backend {

	private static final int CHEST_MENU_SLOT = 6;
	private static final double ATTACK_REACH = 3.0D;

	private static LocalPlayer player() {
		Minecraft client = Minecraft.getInstance();
		return client == null ? null : client.player;
	}

	private static Level level() {
		Minecraft client = Minecraft.getInstance();
		return client == null ? null : client.level;
	}

	@Override
	public boolean available() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.player != null && client.level != null;
	}

	@Override
	public double[] pos() {
		LocalPlayer p = player();
		return p == null ? new double[0] : new double[] {p.getX(), p.getY(), p.getZ()};
	}

	@Override
	public double[] motion() {
		LocalPlayer p = player();
		return p == null ? new double[0]
				: new double[] {p.getDeltaMovement().x, p.getDeltaMovement().y, p.getDeltaMovement().z};
	}

	@Override
	public boolean onGround() {
		LocalPlayer p = player();
		return p != null && p.onGround();
	}

	@Override
	public float fallDistance() {
		LocalPlayer p = player();
		return p == null ? 0.0F : (float) p.fallDistance;
	}

	@Override
	public boolean sneaking() {
		LocalPlayer p = player();
		return p != null && p.isShiftKeyDown();
	}

	@Override
	public double pitch() {
		LocalPlayer p = player();
		return p == null ? 0.0D : p.getXRot();
	}

	@Override
	public boolean gliding() {
		LocalPlayer p = player();
		return p != null && p.isFallFlying();
	}

	@Override
	public double groundDistance() {
		return groundDistance(48);
	}

	@Override
	public double groundDistance(int maxScan) {
		LocalPlayer p = player();
		Level level = level();
		if (p == null || level == null) {
			return Double.MAX_VALUE;
		}
		int x = (int) Math.floor(p.getX());
		int z = (int) Math.floor(p.getZ());
		int startY = (int) Math.floor(p.getY() - 0.001D);
		int limit = Math.max(1, Math.min(maxScan, 320));
		for (int y = startY; y >= startY - limit; y--) {
			if (isSolid(level, x, y, z)) {
				return p.getY() - (y + 1.0D);
			}
		}
		return Double.MAX_VALUE;
	}

	@Override
	public String chestItem() {
		LocalPlayer p = player();
		if (p == null) {
			return "";
		}
		ItemStack stack = p.getItemBySlot(EquipmentSlot.CHEST);
		return stack.isEmpty() ? "" : idOf(stack);
	}

	@Override
	public int hotbarSlot(String itemId) {
		LocalPlayer p = player();
		if (p == null || itemId == null || itemId.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < 9; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			if (!stack.isEmpty() && itemId.equals(idOf(stack))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int selectedSlot() {
		LocalPlayer p = player();
		return p == null ? 0 : p.getInventory().getSelectedSlot();
	}

	@Override
	public void selectSlot(int slot) {
		LocalPlayer p = player();
		if (p != null && slot >= 0 && slot <= 8) {
			p.getInventory().setSelectedSlot(slot);
		}
	}

	@Override
	public boolean equipChestArmor() {
		LocalPlayer p = player();
		if (p == null || !"minecraft:elytra".equals(chestItem())) {
			return false;
		}
		int armorIndex = findBestChestArmor(p);
		if (armorIndex < 0) {
			return false;
		}
		int menuSlot = menuSlotOf(armorIndex);
		click(p, menuSlot);
		click(p, CHEST_MENU_SLOT);
		click(p, menuSlot);
		return true;
	}

	@Override
	public boolean maceInInventory() {
		LocalPlayer p = player();
		return p != null && findBestMace(p) >= 0;
	}

	@Override
	public boolean equipElytra() {
		LocalPlayer p = player();
		if (p == null || "minecraft:elytra".equals(chestItem())) {
			return false;
		}
		int index = findItem(p, "minecraft:elytra");
		if (index < 0) {
			return false;
		}
		// 与 equipChestArmor 完全对称：拿起鞘翅 → 放进胸甲槽（原胸甲被拾起）→ 原胸甲放回原槽。
		int menuSlot = menuSlotOf(index);
		click(p, menuSlot);
		click(p, CHEST_MENU_SLOT);
		click(p, menuSlot);
		return true;
	}

	@Override
	public boolean hasElytra() {
		LocalPlayer p = player();
		return p != null && findItem(p, "minecraft:elytra") >= 0;
	}

	@Override
	public String handItem() {
		LocalPlayer p = player();
		if (p == null) {
			return "";
		}
		ItemStack stack = p.getMainHandItem();
		return stack.isEmpty() ? "" : idOf(stack);
	}

	@Override
	public boolean maceToHotbar() {
		LocalPlayer p = player();
		if (p == null) {
			return false;
		}
		int best = findBestMace(p);
		if (best < 0) {
			return false;
		}
		if (best < 9) {
			selectSlot(best);
			return true;
		}
		int selected = selectedSlot();
		clickSwap(p, menuSlotOf(best), selected);
		selectSlot(selected);
		return true;
	}

	/** 致密（Density）附魔的注册名：重锤每下落一格的额外伤害全靠它。 */
	private static final String DENSITY = "minecraft:density";

	/**
	 * 背包里最值得用的那把重锤：致密等级最高；同级时附魔条数多的优先（风爆等）；
	 * 再同级取靠前的（热键栏优先）。
	 *
	 * <p>没有致密的重锤和+5 致密的重锤伤害差一倍以上，所以不能「见到重锤就切」。</p>
	 */
	private static int findBestMace(LocalPlayer p) {
		int best = -1;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			if (stack.isEmpty() || !"minecraft:mace".equals(idOf(stack))) {
				continue;
			}
			// 致密是主导项（每级都实打实换成伤害），附魔条数只做同级时的加减分
			int score = densityLevel(stack) * 1000 + stack.getEnchantments().size();
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	/**
	 * 物品上的致密等级，没有附魔就是 0。
	 *
	 * <p>26.2 的附魔在数据组件里：{@code ItemEnchantments#keySet()} 是
	 * {@code Holder<Enchantment>}，{@code getLevel(Holder)} 拿等级。</p>
	 *
	 * <p><b>这里不许出现 stonecutter 的条件注释语法</b>——named26 是 javac
	 * 直接编译的，条件分支只会变成块注释，整段逻辑等于不存在
	 * （曾经因此让所有重锤的致密等级都读成 0，永远切到背包里靠前的那把）。
	 * {@code tools/build_named26.py} 会在构建前直接拦下这种写法。</p>
	 */
	private static int densityLevel(ItemStack stack) {
		ItemEnchantments enchantments = stack.getEnchantments();
		for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder
				: enchantments.keySet()) {
			if (DENSITY.equals(holder.getRegisteredName())) {
				return enchantments.getLevel(holder);
			}
		}
		return 0;
	}

	@Override
	public boolean isSolid(int x, int y, int z) {
		Level level = level();
		return level != null && isSolid(level, x, y, z);
	}

	@Override
	public double blockHeight(int x, int y, int z) {
		Level level = level();
		if (level == null) {
			return 0.0D;
		}
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = level.getBlockState(pos);
		if (!state.blocksMotion()) {
			return 0.0D;
		}
		return state.getCollisionShape(level, pos).max(Direction.Axis.Y);
	}

	@Override
	public int nearbyLiving(double radius) {
		return livingAround(radius).size();
	}

	@Override
	public EntityView[] entities(double radius) {
		List<Entity> raw = attackables(radius);
		EntityView[] out = new EntityView[raw.size()];
		for (int i = 0; i < raw.size(); i++) {
			Entity e = raw.get(i);
			Vec3 m = e.getDeltaMovement();
			out[i] = new EntityView(
					BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),
					e.getX(), e.getY(), e.getZ(),
					m.x, m.y, m.z,
					e.getBbWidth(), e.getBbHeight(),
					e instanceof LivingEntity);
		}
		return out;
	}

	@Override
	public boolean attackNearest(double radius) {
		LocalPlayer p = player();
		Minecraft client = Minecraft.getInstance();
		if (p == null || client == null || client.gameMode == null) {
			return false;
		}
		double limit = Math.min(radius, ATTACK_REACH);
		Entity nearest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity entity : livingAround(limit)) {
			double d = p.distanceToSqr(entity);
			if (d < best) {
				best = d;
				nearest = entity;
			}
		}
		if (nearest == null) {
			for (Entity entity : attackables(limit)) {
				if (entity instanceof LivingEntity) {
					continue;
				}
				double d = p.distanceToSqr(entity);
				if (d < best) {
					best = d;
					nearest = entity;
				}
			}
		}
		if (nearest == null) {
			return false;
		}
		client.gameMode.attack(p, nearest);
		return true;
	}

	@Override
	public String boatItemId() {
		LocalPlayer p = player();
		if (p == null) {
			return "";
		}
		for (int i = 0; i < 36; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String id = idOf(stack);
			if (id.endsWith("_boat") || id.endsWith("_raft")) {
				return id;
			}
		}
		return "";
	}

	@Override
	public boolean holdItem(String itemId) {
		LocalPlayer p = player();
		if (p == null || itemId == null || itemId.isEmpty()) {
			return false;
		}
		for (int i = 0; i < 9; i++) {
			if (itemId.equals(idOf(p.getInventory().getItem(i)))) {
				selectSlot(i);
				return true;
			}
		}
		for (int i = 9; i < 36; i++) {
			if (itemId.equals(idOf(p.getInventory().getItem(i)))) {
				int selected = selectedSlot();
				clickSwap(p, menuSlotOf(i), selected);
				selectSlot(selected);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean placeItemDown() {
		LocalPlayer p = player();
		Minecraft client = Minecraft.getInstance();
		Level level = level();
		if (p == null || client == null || client.gameMode == null || level == null) {
			return false;
		}
		int x = (int) Math.floor(p.getX());
		int z = (int) Math.floor(p.getZ());
		int footY = (int) Math.floor(p.getY() + 0.001D);
		for (int dy = 1; dy <= 6; dy++) {
			int y = footY - dy;
			if (!isSolid(level, x, y, z)) {
				continue;
			}
			// 跟玩家自己低头右键完全等价：点的是脚下那格的顶面
			BlockHitResult hit = new BlockHitResult(
					new Vec3(x + 0.5D, y + 1.0D, z + 0.5D), Direction.UP,
					new BlockPos(x, y, z), false);
			client.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
			return true;
		}
		return false;
	}

	@Override
	public boolean mountNearestBoat(double radius) {
		LocalPlayer p = player();
		Minecraft client = Minecraft.getInstance();
		if (p == null || client == null || client.gameMode == null) {
			return false;
		}
		Entity nearest = null;
		double best = Double.MAX_VALUE;
		for (Entity entity : attackables(radius)) {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			if (!id.endsWith("_boat") && !id.endsWith("_raft")) {
				continue;
			}
			double d = p.distanceToSqr(entity);
			if (d < best) {
				best = d;
				nearest = entity;
			}
		}
		if (nearest == null) {
			return false;
		}
		client.gameMode.interact(p, nearest, new EntityHitResult(nearest), InteractionHand.MAIN_HAND);
		return true;
	}

	@Override
	public boolean riding() {
		LocalPlayer p = player();
		return p != null && p.getVehicle() != null;
	}

	@Override
	public boolean containerOpen() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.gui == null) {
			return false;
		}
		return client.gui.screen() instanceof AbstractContainerScreen;
	}

	/**
	 * 往返延迟（毫秒）：取 tab 列表里自己那一项，也就是原版玩家列表界面上显示的数字。
	 *
	 * <p>单人本地游戏几乎恒为 0；刚进服务器、列表还没下发时也可能读到 0。
	 * 拿不到就返回 0，让调用方按「无延迟」处理，而不是抛异常。</p>
	 */
	@Override
	public int ping() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getConnection() == null || client.player == null) {
			return 0;
		}
		PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
		return info == null ? 0 : Math.max(0, info.getLatency());
	}

	// ---------------------------------------------------------------- 内部工具

	private static String idOf(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static boolean isSolid(Level level, int x, int y, int z) {
		return level.getBlockState(new BlockPos(x, y, z)).blocksMotion();
	}

	/** 背包（热键栏 + 主背包，0~35）里第一件该物品的槽位；没有返回 {@code -1}。 */
	private static int findItem(LocalPlayer p, String itemId) {
		for (int i = 0; i < 36; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			if (!stack.isEmpty() && itemId.equals(idOf(stack))) {
				return i;
			}
		}
		return -1;
	}

	private static int findBestChestArmor(LocalPlayer p) {
		int best = -1;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			if (stack.isEmpty() || "minecraft:elytra".equals(idOf(stack))) {
				continue;
			}
			if (p.getEquipmentSlotForItem(stack) != EquipmentSlot.CHEST) {
				continue;
			}
			int score = armorScore(stack);
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	private static int armorScore(ItemStack stack) {
		int enchants = 0;
		try {
			enchants = stack.getEnchantments().size();
		} catch (Throwable ignored) {
			// 附魔读取失败就只按耐久排
		}
		return enchants * 10000 + Math.min(stack.getMaxDamage(), 9999);
	}

	private static int menuSlotOf(int inventoryIndex) {
		return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
	}

	private static void click(LocalPlayer p, int menuSlot) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.gameMode != null) {
			client.gameMode.handleContainerInput(p.inventoryMenu.containerId,
					menuSlot, 0, ContainerInput.PICKUP, p);
		}
	}

	private static void clickSwap(LocalPlayer p, int menuSlot, int hotbarSlot) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.gameMode != null) {
			client.gameMode.handleContainerInput(p.inventoryMenu.containerId,
					menuSlot, hotbarSlot, ContainerInput.SWAP, p);
		}
	}

	private static List<LivingEntity> livingAround(double radius) {
		LocalPlayer p = player();
		Level level = level();
		if (p == null || level == null) {
			return List.of();
		}
		List<Entity> raw = level.getEntities(p, p.getBoundingBox().inflate(radius),
				e -> e instanceof LivingEntity && e != p && e.isAlive() && e.isPickable());
		List<LivingEntity> result = new ArrayList<>(raw.size());
		for (Entity entity : raw) {
			if (entity instanceof LivingEntity living) {
				result.add(living);
			}
		}
		return result;
	}

	/** 准星能选中的一切实体（生物、船、盔甲架…），掉落物与投掷物不在内。 */
	private static List<Entity> attackables(double radius) {
		LocalPlayer p = player();
		Level level = level();
		if (p == null || level == null) {
			return List.of();
		}
		return level.getEntities(p, p.getBoundingBox().inflate(radius),
				e -> e != p && e.isAlive() && e.isPickable());
	}
}
