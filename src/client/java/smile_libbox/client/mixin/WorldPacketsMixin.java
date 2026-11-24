package smile_libbox.client.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smile_libbox.api.world.Packets;

/**
 * 容器操作的黑箱拦截点。
 *
 * <p>{@code MultiPlayerGameMode#handleInventoryMouseClick} 是客户端「改背包 / 换装备」
 * 的唯一集中入口：原版键位、拖拽、以及绝大多数自动换装模组都从这里走，
 * 本地预测与 C2S 包也都在这个方法内部发生。在 {@code HEAD} 处取消，
 * 等于这条操作从没发生过 —— 不会发包，也不会有任何界面抖动。</p>
 *
 * <p>本类只做「把这次操作翻译成纯 Java 的 {@link Packets.Action}」这一件事，
 * 具体放不放行由使用方注册的规则决定。</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class WorldPacketsMixin {

	/** {@code InventoryMenu} 里装备槽的下标区间与胸甲槽位置（原版固定布局）。 */
	private static final int ARMOR_SLOT_MIN = 5;
	private static final int ARMOR_SLOT_MAX = 8;
	private static final int CHEST_SLOT = 6;

	@Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
	private void smile_libbox$filter(int containerId, int slotId, int button, ClickType clickType,
			Player player, CallbackInfo ci) {
		if (player == null || !Packets.hasRules()) {
			return;
		}
		String clickName = clickType == null ? "" : clickType.name();
		String itemId = "";
		String carriedId = "";
		String hotbarId = "";
		try {
			// 只有玩家背包菜单里 5~8 才是装备槽；箱子菜单里同一下标是箱子格子
			Slot slot = player.inventoryMenu.getSlot(slotId);
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			}
		} catch (Throwable ignored) {
			// 槽位越界等情况：当作空槽处理，规则自己决定要不要放行
		}
		try {
			// 光标上的东西：换装是「拿起鞘翅 → 点胸甲槽」，第二下只能从这里认出来
			ItemStack carried = player.containerMenu.getCarried();
			if (!carried.isEmpty()) {
				carriedId = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
			}
		} catch (Throwable ignored) {
			// 菜单切换瞬间可能取不到，按空手处理
		}
		try {
			// 数字键交换：按下的那个热键栏槽里是什么
			if ("SWAP".equals(clickName) && button >= 0 && button < 9) {
				ItemStack hotbar = player.getInventory().getItem(button);
				if (!hotbar.isEmpty()) {
					hotbarId = BuiltInRegistries.ITEM.getKey(hotbar.getItem()).toString();
				}
			}
		} catch (Throwable ignored) {
			// 同上
		}
		Packets.Action action = new Packets.Action("container_click", containerId, slotId, button,
				clickName, itemId, carriedId, hotbarId,
				slotId == CHEST_SLOT,
				slotId >= ARMOR_SLOT_MIN && slotId <= ARMOR_SLOT_MAX);
		if (Packets.shouldBlock(action)) {
			ci.cancel();
		}
	}
}
