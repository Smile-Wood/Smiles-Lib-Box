package smile_libbox.client.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smile_libbox.api.world.Packets;

/**
 * 26.2 的容器操作黑箱拦截点。
 *
 * <p>方法名与枚举都换过：{@code handleInventoryMouseClick} → {@code handleContainerInput}，
 * {@code ClickType} → {@code ContainerInput}。语义不变，仍在发包与本地预测之前。</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class WorldPacketsMixin {

	private static final int ARMOR_SLOT_MIN = 5;
	private static final int ARMOR_SLOT_MAX = 8;
	private static final int CHEST_SLOT = 6;

	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void smile_libbox$filter(int containerId, int slotId, int button, ContainerInput input,
			Player player, CallbackInfo ci) {
		if (player == null || !Packets.hasRules()) {
			return;
		}
		String clickName = input == null ? "" : input.name();
		String itemId = "";
		String carriedId = "";
		String hotbarId = "";
		try {
			Slot slot = player.inventoryMenu.getSlot(slotId);
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			}
		} catch (Throwable ignored) {
			// 槽位越界按空槽处理
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
