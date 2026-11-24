package smile_libbox.api.world;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 出站「容器操作包」的黑箱拦截层。
 *
 * <p>使用方不需要知道 {@code ServerboundContainerClickPacket} / {@code ClickType} /
 * {@code InventoryMenu} 这些类型，只要注册一条规则：这条操作该不该放行。
 * 库在客户端发包的唯一入口上做判定，命中就直接把包丢掉（原版连本地预测都不会发生）。</p>
 *
 * <h2>典型用法：护甲锁</h2>
 * <pre>{@code
 * Packets.addRule(action -> armorLocked
 *         && "minecraft:elytra".equals(action.carriedId)   // 手上拿着鞘翅
 *         && action.chestSlot);                            // 点的是胸甲槽
 * }</pre>
 * <p>含义：锁定期间，任何「往胸甲槽放鞘翅」的容器操作都不许发出去——
 * 不管是玩家点的、还是别的模组自动换的。</p>
 *
 * <p>只判 {@code itemId}（被点槽位里现有的东西）是不够的：换装是「拿起鞘翅 → 点胸甲槽」
 * 两步，第二步槽里还是旧胸甲。{@link Action#carriedId} 与 {@link Action#hotbarId}
 * 就是为此补上的两个方向。</p>
 *
 * <p>规则在客户端主线程上逐条调用，应当保持轻量（几次字段比较即可）。
 * 任何规则抛异常都按「放行」处理，绝不能让一条坏规则卡死所有背包操作。</p>
 */
public final class Packets {

	private static final List<Rule> RULES = new CopyOnWriteArrayList<>();
	private static volatile long blockedCount;

	private Packets() {
	}

	/** 一条出站容器操作的只读视图。所有字段都是纯 Java。 */
	public static final class Action {

		/** 操作种类，目前只有 {@code "container_click"}。 */
		public final String kind;
		public final int containerId;
		/** 目标槽位（{@code InventoryMenu} 的下标）。 */
		public final int slot;
		public final int button;
		/** {@code PICKUP} / {@code QUICK_MOVE} / {@code SWAP} / {@code THROW} / … */
		public final String clickType;
		/** 该槽当前物品的注册名；空槽为 {@code ""}。 */
		public final String itemId;
		/**
		 * 光标上拿着的物品注册名（{@code AbstractContainerMenu#getCarried()}）；空手为 {@code ""}。
		 *
		 * <p>换装的实际形态是「先点背包里的鞘翅拿起、再点胸甲槽放下」。第二步点的是胸甲槽，
		 * 而那时槽里还是旧的胸甲，{@link #itemId} 是胸甲——光看槽内物品根本认不出
		 * 「这一下要把鞘翅装进去」。判断方向必须靠这个字段。</p>
		 */
		public final String carriedId;
		/**
		 * 数字键交换（{@code SWAP}）时按下的那个热键栏槽里的物品注册名；其它操作为 {@code ""}。
		 *
		 * <p>「按 1~9 把鞘翅直接换到胸甲槽」不会经过光标，只看 {@link #carriedId} 同样会漏。</p>
		 */
		public final String hotbarId;
		/** 是不是胸甲装备槽。 */
		public final boolean chestSlot;
		/** 是不是任意装备槽（头 / 胸 / 腿 / 脚）。 */
		public final boolean armorSlot;

		public Action(String kind, int containerId, int slot, int button,
				String clickType, String itemId, boolean chestSlot, boolean armorSlot) {
			this(kind, containerId, slot, button, clickType, itemId, "", "", chestSlot, armorSlot);
		}

		public Action(String kind, int containerId, int slot, int button, String clickType,
				String itemId, String carriedId, String hotbarId,
				boolean chestSlot, boolean armorSlot) {
			this.kind = kind == null ? "" : kind;
			this.containerId = containerId;
			this.slot = slot;
			this.button = button;
			this.clickType = clickType == null ? "" : clickType;
			this.itemId = itemId == null ? "" : itemId;
			this.carriedId = carriedId == null ? "" : carriedId;
			this.hotbarId = hotbarId == null ? "" : hotbarId;
			this.chestSlot = chestSlot;
			this.armorSlot = armorSlot;
		}

		@Override
		public String toString() {
			return kind + "(slot=" + slot + ", button=" + button + ", type=" + clickType
					+ ", item=" + itemId + ", carried=" + carriedId
					+ (hotbarId.isEmpty() ? "" : ", hotbar=" + hotbarId) + ")";
		}
	}

	/** 返回 {@code true} 表示这条操作不允许发出。 */
	@FunctionalInterface
	public interface Rule {
		boolean block(Action action);
	}

	public static void addRule(Rule rule) {
		if (rule != null && !RULES.contains(rule)) {
			RULES.add(rule);
		}
	}

	public static void removeRule(Rule rule) {
		RULES.remove(rule);
	}

	public static void clearRules() {
		RULES.clear();
	}

	public static boolean hasRules() {
		return !RULES.isEmpty();
	}

	/** 已丢弃的操作条数（调试用）。 */
	public static long blockedCount() {
		return blockedCount;
	}

	public static void resetBlockedCount() {
		blockedCount = 0;
	}

	/** 由库的注入点调用；使用方不要直接调。 */
	public static boolean shouldBlock(Action action) {
		if (action == null || RULES.isEmpty()) {
			return false;
		}
		for (Rule rule : RULES) {
			try {
				if (rule.block(action)) {
					blockedCount++;
					return true;
				}
			} catch (Throwable t) {
				// 坏规则绝不能让玩家的背包操作全部卡死
				System.err.println("[smile_libbox] Packets 规则异常，按放行处理: " + t);
			}
		}
		return false;
	}
}
