package smile_libbox.api.world;

/**
 * 某一 tick 的实体快照（只读）。
 *
 * <p>这个类里只有 Java 原生类型，{@code Entity} / {@code Vec3} / {@code AABB} 都被
 * 摊平成了数字，所以依赖方可以拿它做纯 Java 的几何判断（落点预判、命中范围），
 * 既不用碰 MC 类型，也能脱离游戏做单元自检。</p>
 *
 * <p>坐标是实体的<b>脚部</b>位置，碰撞箱由 {@link #width} 与 {@link #height} 描述：
 * 水平范围是 {@code x ± width / 2}、{@code z ± width / 2}，竖直范围是
 * {@code [y, y + height]}。船（宽 1.375 / 高 0.5625）与盔甲架（0.5 / 1.975）
 * 都按同样的方式描述，调用方不需要区分类型。</p>
 */
public final class EntityView {

	/** 注册名，例如 {@code "minecraft:oak_boat"}、{@code "minecraft:armor_stand"}。 */
	public final String id;

	/** 脚部坐标。 */
	public final double x;
	public final double y;
	public final double z;

	/** 速度（格/tick）。 */
	public final double dx;
	public final double dy;
	public final double dz;

	/** 碰撞箱宽（水平两轴相同）与高。 */
	public final double width;
	public final double height;

	/** 是不是活体（船为 {@code false}，盔甲架为 {@code true}）。 */
	public final boolean living;

	public EntityView(String id, double x, double y, double z,
			double dx, double dy, double dz, double width, double height, boolean living) {
		this.id = id == null ? "" : id;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
		this.width = width;
		this.height = height;
		this.living = living;
	}

	/** 水平半径。 */
	public double halfWidth() {
		return this.width / 2.0D;
	}

	/** 脚部到玩家同一水平面的距离（用 {@code y} 之外的轴）。 */
	public double horizontalDistance(double px, double pz) {
		double ddx = this.x - px;
		double ddz = this.z - pz;
		return Math.sqrt(ddx * ddx + ddz * ddz);
	}

	@Override
	public String toString() {
		return this.id + "@" + String.format("%.1f,%.1f,%.1f", this.x, this.y, this.z);
	}
}
