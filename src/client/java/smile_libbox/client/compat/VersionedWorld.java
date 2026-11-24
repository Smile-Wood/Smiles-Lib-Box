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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import smile_libbox.api.world.EntityView;
import smile_libbox.api.world.World;

import java.util.List;

/**
 * 1.20.1 ~ 1.21.11（intermediary）的 {@link World.Backend} 实现。
 *
 * <p>本类由库按每个游戏版本单独编译，所以这里可以直接写该版本的 MC 描述符；
 * 跨版本会变签名的调用集中在 {@link McCalls} 之类的条件注释里，使用方看不到。</p>
 *
 * <p>所有方法都允许在没有玩家 / 没有世界时被调用（主菜单、加载中、断线），
 * 一律返回空值而不是抛异常。</p>
 */
public final class VersionedWorld implements World.Backend {

	/** 原版 {@code InventoryMenu} 里胸甲装备槽的下标。 */
	private static final int CHEST_MENU_SLOT = 6;

	/** 玩家的交互距离（原版 3 格），攻击目标必须在这个范围内。 */
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

	// ---------------------------------------------------------------- 玩家状态

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
		// 1.21.11 起原版把这个字段改成了 double，统一在这里收窄。
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
		// yarn 在 1.21.2 一度把它改叫 isGliding，1.21.11 又并回了 isFallFlying；
		// 以最高节点为准用旧名字（1.20.1 起一直有效）。
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

	// ---------------------------------------------------------------- 装备

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
		if (p == null) {
			return 0;
		}
		//? if >=1.21.5 {
		/*return p.getInventory().getSelectedSlot();
		*///?} else {
		return p.getInventory().selected;
		//?}
	}

	@Override
	public void selectSlot(int slot) {
		LocalPlayer p = player();
		if (p == null || slot < 0 || slot > 8) {
			return;
		}
		//? if >=1.21.5 {
		/*p.getInventory().setSelectedSlot(slot);
		*///?} else {
		p.getInventory().selected = slot;
		//?}
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
		// 原版路径：本地预测 + 发包，服务端权威（多人服同样生效）。
		// 拿起胸甲 → 放进胸甲槽（鞘翅被拾起）→ 鞘翅放回原槽。
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
		// 走的是本地预测 + 发包，服务端权威，多人服同样生效。
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
		// 只在背包里：用「按数字键交换」把背包槽换到当前选中的热键栏槽。
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

	private static int findBestMace(LocalPlayer p) {
		int best = -1;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			if (stack.isEmpty() || !"minecraft:mace".equals(idOf(stack))) {
				continue;
			}
			// 致密是主导项（每级都实打实换成伤害），附魔条数只做同级时的加减分
			int score = densityLevel(stack) * 1000 + enchantCount(stack);
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	/** 这件物品上有多少条附魔（含自定义名以外的全部附魔条目）。 */
	private static int enchantCount(ItemStack stack) {
		//? if >=1.20.5 {
		/*return stack.getEnchantments().size();
		*///?} else {
		return 0;
		//?}
	}

	/**
	 * 物品上的致密等级，没有附魔就是 0。
	 *
	 * <p>1.20.5 起附魔搬进数据组件，{@code ItemStack#getEnchantments()} 能直接读；
	 * 更早的版本连重锤都没有，返回 0 就够。</p>
	 */
	private static int densityLevel(ItemStack stack) {
		//? if >=1.21 {
		/*for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder
				: stack.getEnchantments().keySet()) {
			if (DENSITY.equals(holder.getRegisteredName())) {
				return stack.getEnchantments().getLevel(holder);
			}
		}
		return 0;
		*///?} else if >=1.20.5 {
		/*// 1.20.6 的 keySet 已经是 Holder，getLevel 还收 Enchantment，只能拆一层
		for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder
				: stack.getEnchantments().keySet()) {
			if (DENSITY.equals(holder.getRegisteredName())) {
				return stack.getEnchantments().getLevel(holder.value());
			}
		}
		return 0;
		*///?} else {
		return 0;
		//?}
	}

	// ---------------------------------------------------------------- 世界

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
		// isPickable() 就是「准星能选中」：船、盔甲架、生物都在内，
		// 掉落物 / 箭 / 经验球不在内，正好是不该被猛击的东西。
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
		// 先挑活体（有击退与猛击伤害），附近没有活体才退而求其次打船 / 盔甲架
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
		// 3 参 interact 在 1.20.1~1.21.11 一直都在（26.2 才并成带 EntityHitResult 的四参）
		client.gameMode.interact(p, nearest, InteractionHand.MAIN_HAND);
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
		return client != null && client.screen instanceof AbstractContainerScreen;
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
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = level.getBlockState(pos);
		return state.blocksMotion();
	}

	/** 背包里最强的一件胸甲：附魔数量优先，其次耐久上限（近似材质品质）。 */
	private static int findBestChestArmor(LocalPlayer p) {
		int best = -1;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = p.getInventory().getItem(i);
			if (stack.isEmpty() || "minecraft:elytra".equals(idOf(stack))) {
				continue;
			}
			if (!isChestArmor(p, stack)) {
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

	private static boolean isChestArmor(LocalPlayer p, ItemStack stack) {
		// 用原版「这件物品该装哪个槽」的判断，避免依赖 ArmorItem.Type 这类按版本重构过的枚举。
		// 1.21 起这个方法从静态搬到了实体实例上。
		//? if >=1.21 {
		/*return p.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
		*///?} else {
		return LivingEntity.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
		//?}
	}

	/**
	 * 护甲强度打分：附魔条数为主，耐久上限为辅。
	 *
	 * <p>原版没有公开的「护甲品质」枚举（而且各版本反复重构），但耐久上限与材质一一对应
	 * （皮革 81 / 金 113 / 锁链与铁 241 / 钻石 529 / 下界合金 592），
	 * 配合附魔条数足够选出「附魔最多、品质最高」的那件。</p>
	 */
	private static int armorScore(ItemStack stack) {
		int enchants = 0;
		try {
			// 1.20.5 起附魔搬进数据组件，改由 ItemStack 自己读
			//? if >=1.20.5 {
			/*enchants = stack.getEnchantments().size();
			*///?} else {
			enchants = EnchantmentHelper.getEnchantments(stack).size();
			//?}
		} catch (Throwable ignored) {
			// 附魔读取失败就只按耐久排，不能让一次异常挑不出护甲
		}
		return enchants * 10000 + Math.min(stack.getMaxDamage(), 9999);
	}

	/** 背包下标（0~35）→ {@code InventoryMenu} 的槽位下标。 */
	private static int menuSlotOf(int inventoryIndex) {
		return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
	}

	private static void click(LocalPlayer p, int menuSlot) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.gameMode != null) {
			client.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId,
					menuSlot, 0, ClickType.PICKUP, p);
		}
	}

	private static void clickSwap(LocalPlayer p, int menuSlot, int hotbarSlot) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.gameMode != null) {
			client.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId,
					menuSlot, hotbarSlot, ClickType.SWAP, p);
		}
	}

	private static List<LivingEntity> livingAround(double radius) {
		LocalPlayer p = player();
		Level level = level();
		if (p == null || level == null) {
			return List.of();
		}
		return level.getEntitiesOfClass(LivingEntity.class,
				p.getBoundingBox().inflate(radius),
				e -> e != p && e.isAlive() && e.isPickable());
	}

	/** 准星能选中的一切实体（生物、船、盔甲架…），掉落物与投掷物不在内。 */
	private static List<Entity> attackables(double radius) {
		LocalPlayer p = player();
		Level level = level();
		if (p == null || level == null) {
			return List.of();
		}
		return level.getEntitiesOfClass(Entity.class,
				p.getBoundingBox().inflate(radius),
				e -> e != p && e.isAlive() && e.isPickable());
	}
}
