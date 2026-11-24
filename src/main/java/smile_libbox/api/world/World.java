package smile_libbox.api.world;

/**
 * 玩家 / 世界的最小只读（加少量装备操作）门面。
 *
 * <p>本类只有 Java 原生类型，常量池里没有任何 {@code net.minecraft} 描述符，
 * 所以依赖方可以把它编进单 jar。真正的 MC 调用在库的按版本适配层里
 * （{@code smile_libbox.client.compat.VersionedWorld} 与 26.2 的对应实现）。</p>
 *
 * <p>库安装之前、或当前环境没装任何适配实现时，所有方法都返回安全默认值
 * （{@code ready() == false}、空数组、{@code 0}、{@code false}），
 * 调用方不需要自己判空。</p>
 *
 * <h2>为什么物品用字符串 id</h2>
 * <p>{@code ItemStack} / {@code Item} 是 MC 类型，跨不了版本。这里统一用
 * 注册名（{@code "minecraft:elytra"}、{@code "minecraft:mace"}、空串表示空槽），
 * 判断与配置都只认字符串；1.20.1 没有重锤，相关方法自然返回 {@code -1} / {@code false}。</p>
 */
public final class World {

	private static volatile Backend backend;

	private World() {
	}

	/**
	 * 由库的客户端入口安装。使用方不要调用。
	 */
	public static void install(Backend implementation) {
		backend = implementation;
	}

	public static boolean ready() {
		Backend b = backend;
		return b != null && b.available();
	}

	private static Backend b() {
		return backend;
	}

	// ---------------------------------------------------------------- 玩家状态

	/** 玩家脚部坐标 {@code {x, y, z}}；无玩家时返回空数组。 */
	public static double[] pos() {
		Backend b = b();
		return b == null ? EMPTY : b.pos();
	}

	/** 玩家速度（格/tick）{@code {dx, dy, dz}}；无玩家时返回空数组。 */
	public static double[] motion() {
		Backend b = b();
		return b == null ? EMPTY : b.motion();
	}

	public static boolean onGround() {
		Backend b = b();
		return b != null && b.onGround();
	}

	/** 已下落高度（格）。 */
	public static float fallDistance() {
		Backend b = b();
		return b == null ? 0.0F : b.fallDistance();
	}

	public static boolean sneaking() {
		Backend b = b();
		return b != null && b.sneaking();
	}

	/** 是否正在滑翔（1.21.2 起原版把 {@code isFallFlying} 改名为 {@code isGliding}，由适配层吸收）。 */
	public static boolean gliding() {
		Backend b = b();
		return b != null && b.gliding();
	}

	/**
	 * 视线俯仰角（度）：{@code -90} 正向上看，{@code 0} 平视，{@code +90} 正向下看。
	 *
	 * <p>给「按住潜行 + 低头盯着脚下目标」这类需要区分意图的功能用——滑翔时潜行键是
	 * 原版的「低头加速下降」，光看按键分不出玩家想减速还是想砸下去。</p>
	 */
	public static double pitch() {
		Backend b = b();
		return b == null ? 0.0D : b.pitch();
	}

	/** 往下找地面的默认格数。 */
	public static final int DEFAULT_GROUND_SCAN = 48;

	/** 距离脚下最近固体方块顶面的高度（格）；落不到地面时返回 {@link Double#MAX_VALUE}。 */
	public static double groundDistance() {
		Backend b = b();
		return b == null ? Double.MAX_VALUE : b.groundDistance();
	}

	/**
	 * 同上，但只往下找 {@code maxScan} 格。
	 *
	 * <p>默认那版只扫 48 格：穿鞘翅飞的高度往往比这高得多，从高空往下砸时
	 * 要一个更深的扫描范围，否则脚下明明是地也判成「悬空」。</p>
	 */
	public static double groundDistance(int maxScan) {
		Backend b = b();
		return b == null ? Double.MAX_VALUE : b.groundDistance(maxScan);
	}

	// ---------------------------------------------------------------- 网络

	/**
	 * 到服务端的往返延迟（毫秒）；单人本地游戏约为 {@code 0}，未知时也是 {@code 0}。
	 *
	 * <p>取的是 tab 列表里自己那一项的延迟，也就是原版玩家列表界面显示的数字。
	 * 它是<b>往返</b>，不是单向：单程约为 {@link #HALF_PING_DIVISOR} 分之一。
	 * 原版每秒刷新一次，短期会有几十毫秒的抖动，需要稳定值请自行平滑。</p>
	 *
	 * <p>用途是给「发包要提前多久」这类判定一个量纲正确的输入 —— 高延迟玩家的攻击包
	 * 在路上要飞半程，落地那一帧再发就晚了。</p>
	 */
	public static int ping() {
		Backend b = b();
		return b == null ? 0 : b.ping();
	}

	/** 往返延迟换算成单程的分母：{@code 单程毫秒 = ping / 2}。 */
	public static final int HALF_PING_DIVISOR = 2;

	// ---------------------------------------------------------------- 装备

	/** 胸甲槽物品的注册名；空槽返回 {@code ""}。 */
	public static String chestItem() {
		Backend b = b();
		String id = b == null ? null : b.chestItem();
		return id == null ? "" : id;
	}

	/** 胸甲槽是不是鞘翅。 */
	public static boolean chestIsElytra() {
		return "minecraft:elytra".equals(chestItem());
	}

	/** 热键栏里第一个该物品的槽位（0~8）；没有返回 {@code -1}。 */
	public static int hotbarSlot(String itemId) {
		Backend b = b();
		return b == null || itemId == null ? -1 : b.hotbarSlot(itemId);
	}

	/** 当前选中的热键栏槽位（0~8）。 */
	public static int selectedSlot() {
		Backend b = b();
		return b == null ? 0 : b.selectedSlot();
	}

	public static void selectSlot(int slot) {
		Backend b = b();
		if (b != null) {
			b.selectSlot(slot);
		}
	}

	/**
	 * 把胸甲槽的鞘翅换回背包、换上背包里护甲值最高的胸甲。
	 *
	 * <p>背包里没有别的胸甲时什么都不做并返回 {@code false}——宁可让鞘翅留着，
	 * 也不能把玩家换到裸奔。</p>
	 */
	public static boolean equipChestArmor() {
		Backend b = b();
		return b != null && b.equipChestArmor();
	}

	/**
	 * 把背包里的鞘翅穿到胸甲槽（原胸甲放回鞘翅原来的位置）。
	 *
	 * <p>是 {@link #equipChestArmor()} 的反向操作：那个是「落地了，把翅膀换成护甲」，
	 * 这个是「又需要飞了，把翅膀穿回去」。滑翔被打断之后要恢复飞行，
	 * 或者被打飞起来却没东西可砸时，都靠它把翅膀装回去。</p>
	 *
	 * <p>胸甲槽已经是鞘翅、或背包里根本没有鞘翅时返回 {@code false}。</p>
	 */
	public static boolean equipElytra() {
		Backend b = b();
		return b != null && b.equipElytra();
	}

	/** 背包（含热键栏）里有没有鞘翅；胸甲槽里那件不算。 */
	public static boolean hasElytra() {
		Backend b = b();
		return b != null && b.hasElytra();
	}

	/** 背包（含热键栏）里有没有重锤；1.20.x 恒为 {@code false}。 */
	public static boolean maceInInventory() {
		Backend b = b();
		return b != null && b.maceInInventory();
	}

	/**
	 * 现在手上（主手）拿的物品注册名；空手返回 {@code ""}。
	 *
	 * <p>换装 / 切武器这类操作会改手，做完要把玩家原来的东西还回去——
	 * 先记下这个值，收工时按它 {@link #holdItem(String)} 换回来。</p>
	 */
	public static String handItem() {
		Backend b = b();
		String id = b == null ? null : b.handItem();
		return id == null ? "" : id;
	}

	/**
	 * 保证重锤在当前选中的热键栏槽里：已经在热键栏就直接切槽，
	 * 只在背包里则通过容器点击包把它换到当前槽（服务端权威，多人服同样有效）。
	 *
	 * @return 是否已就绪（当前选中槽是重锤）
	 */
	public static boolean maceToHotbar() {
		Backend b = b();
		return b != null && b.maceToHotbar();
	}

	// ---------------------------------------------------------------- 世界

	/** 该坐标是否是可站立的固体方块。 */
	public static boolean isSolid(int x, int y, int z) {
		Backend b = b();
		return b != null && b.isSolid(x, y, z);
	}

	/**
	 * 该坐标方块的<b>碰撞高度</b>（0 ~ 1 格）。
	 *
	 * <p>地毯 {@code 0.0625}、雪层按层数、台阶 {@code 0.5}、完整方块 {@code 1.0}、
	 * 空气 / 无碰撞（火把、告示牌）{@code 0.0}。返回值可能大于 1（栅栏、墙），
	 * 调用方只该关心「是不是满格」时按 {@code >= 1} 判。</p>
	 *
	 * <p>为什么需要它：{@link #isSolid} 对地毯和石头都返回 {@code true}，
	 * 用它数净空会把「脚下垫了一层地毯」当成「头顶被挡了一格」，
	 * 一个四格高的走廊会被量成三格。</p>
	 */
	public static double blockHeight(int x, int y, int z) {
		Backend b = b();
		return b == null ? 0.0D : b.blockHeight(x, y, z);
	}

	/** {@code radius} 格内的活体数量（不含自己、不含已死亡）。 */
	public static int nearbyLiving(double radius) {
		Backend b = b();
		return b == null ? 0 : b.nearbyLiving(radius);
	}

	/**
	 * {@code radius} 格内所有<b>可被准星选中</b>的实体的快照（不含自己）。
	 *
	 * <p>范围比 {@link #nearbyLiving} 宽：船、盔甲架这类非活体也算，因为它们确实能被
	 * 打中、也确实挡路；掉落物、箭、经验球不 {@code isPickable()}，自然被排除。</p>
	 *
	 * <p>返回的数组是快照，调用方随便存、随便做几何运算；每次调用都会重新查询。</p>
	 */
	public static EntityView[] entities(double radius) {
		Backend b = b();
		return b == null ? EMPTY_ENTITIES : b.entities(radius);
	}

	// ---------------------------------------------------------------- 放船自救

	/** 背包里第一艘船（含运输船 / 竹筏）的注册名；没有返回空串。 */
	public static String boatItemId() {
		Backend b = b();
		String id = b == null ? null : b.boatItemId();
		return id == null ? "" : id;
	}

	/**
	 * 保证指定物品在手上：热键栏里就直接切槽，只在背包里就用容器点击包换到当前槽。
	 *
	 * @return 是否已选中（{@code true} 表示当前手持的就是它）
	 */
	public static boolean holdItem(String itemId) {
		Backend b = b();
		return b != null && itemId != null && b.holdItem(itemId);
	}

	/**
	 * 把视线压到脚下最近的那格方块顶面，用手里的东西右击一次——放船 / 放方块都用它。
	 *
	 * <p>走的是原版 {@code useItemOn} 的路径（跟玩家自己右键一样要过服务端校验），
	 * 所以船要真的放得下才会出现。</p>
	 *
	 * @return 是否发出了这次使用
	 */
	public static boolean placeItemDown() {
		Backend b = b();
		return b != null && b.placeItemDown();
	}

	/**
	 * 右键 {@code radius} 格内最近的船上去（船 / 木筏 / 运输船都算）。
	 *
	 * @return 是否真的发出了交互
	 */
	public static boolean mountNearestBoat(double radius) {
		Backend b = b();
		return b != null && b.mountNearestBoat(radius);
	}

	/** 是否正骑着东西（船 / 矿车 / 生物）——骑着船时别再去打船。 */
	public static boolean riding() {
		Backend b = b();
		return b != null && b.riding();
	}

	/**
	 * 攻击 {@code radius} 格内最近的活体（走原版的攻击路径，会正常发交互包）。
	 *
	 * @return 是否真的打出去了
	 */
	public static boolean attackNearest(double radius) {
		Backend b = b();
		return b != null && b.attackNearest(radius);
	}

	/** 当前是否开着容器 / 背包界面——用来区分「玩家自己换的」与「模组自动换的」。 */
	public static boolean containerOpen() {
		Backend b = b();
		return b != null && b.containerOpen();
	}

	private static final double[] EMPTY = new double[0];

	private static final EntityView[] EMPTY_ENTITIES = new EntityView[0];

	/**
	 * 由库的适配层实现。所有方法都允许在无玩家（主菜单、加载中）时被调用，
	 * 实现方必须自己判空而不是抛异常。
	 */
	public interface Backend {

		Backend NOOP = new Backend() {
			public boolean available() { return false; }
			public double[] pos() { return EMPTY; }
			public double[] motion() { return EMPTY; }
			public boolean onGround() { return false; }
			public float fallDistance() { return 0.0F; }
			public boolean sneaking() { return false; }
			public boolean gliding() { return false; }
			public double pitch() { return 0.0D; }
			public double groundDistance() { return Double.MAX_VALUE; }
			public double groundDistance(int maxScan) { return Double.MAX_VALUE; }
			public String chestItem() { return ""; }
			public int hotbarSlot(String itemId) { return -1; }
			public int selectedSlot() { return 0; }
			public void selectSlot(int slot) { }
			public boolean equipChestArmor() { return false; }
			public boolean maceInInventory() { return false; }
			public boolean maceToHotbar() { return false; }
			public boolean isSolid(int x, int y, int z) { return false; }
			public int nearbyLiving(double radius) { return 0; }
			public EntityView[] entities(double radius) { return EMPTY_ENTITIES; }
			public boolean attackNearest(double radius) { return false; }
			public String boatItemId() { return ""; }
			public boolean holdItem(String itemId) { return false; }
			public boolean placeItemDown() { return false; }
			public boolean mountNearestBoat(double radius) { return false; }
			public boolean riding() { return false; }
			public boolean containerOpen() { return false; }
		};

		boolean available();

		double[] pos();

		double[] motion();

		boolean onGround();

		float fallDistance();

		boolean sneaking();

		boolean gliding();

		/** 视线俯仰角（度），-90 向上、+90 向下；默认 0（平视）。 */
		default double pitch() {
			return 0.0D;
		}

		double groundDistance();

		/** 同 {@link #groundDistance()}，但只往下找 {@code maxScan} 格；默认实现退回前者。 */
		default double groundDistance(int maxScan) {
			return groundDistance();
		}

		/** 到服务端的往返延迟（毫秒）；默认 {@code 0}（未知），旧适配层不改也能编译。 */
		default int ping() {
			return 0;
		}

		String chestItem();

		int hotbarSlot(String itemId);

		int selectedSlot();

		void selectSlot(int slot);

		boolean equipChestArmor();

		/** 同 {@link World#equipElytra()}；默认不支持，旧适配层不必跟着改。 */
		default boolean equipElytra() {
			return false;
		}

		/** 同 {@link World#hasElytra()}；默认没有，旧适配层不必跟着改。 */
		default boolean hasElytra() {
			return false;
		}

		boolean maceInInventory();

		boolean maceToHotbar();

		/** 主手物品的注册名；默认空串（旧实现不必跟着改）。 */
		default String handItem() {
			return "";
		}

		boolean isSolid(int x, int y, int z);

		/**
		 * 该坐标方块的碰撞高度（0 ~ 1 格）。
		 *
		 * <p>默认实现把「是否固体」当作满格，旧适配层不改也能编译；
		 * 但那样就分不出地毯 / 雪层，所以两份版本适配层都自己实现了。</p>
		 */
		default double blockHeight(int x, int y, int z) {
			return isSolid(x, y, z) ? 1.0D : 0.0D;
		}

		int nearbyLiving(double radius);

		/**
		 * 可被准星选中的实体快照。默认返回空数组，旧实现不必跟着改。
		 */
		default EntityView[] entities(double radius) {
			return EMPTY_ENTITIES;
		}

		boolean attackNearest(double radius);

		boolean containerOpen();

		/** 背包里第一艘船的注册名；默认空串。 */
		default String boatItemId() {
			return "";
		}

		/** 把指定物品弄到手上；默认做不到。 */
		default boolean holdItem(String itemId) {
			return false;
		}

		/** 对准脚下地面用一次手里的东西；默认做不到。 */
		default boolean placeItemDown() {
			return false;
		}

		/** 右键最近的船上船；默认做不到。 */
		default boolean mountNearestBoat(double radius) {
			return false;
		}

		/** 是否正骑着东西；默认否。 */
		default boolean riding() {
			return false;
		}
	}
}
