package smile_libbox.client;

import net.fabricmc.api.ClientModInitializer;
import smile_libbox.api.debug.Debug;
import smile_libbox.api.ui.Ui;
import smile_libbox.api.world.World;
import smile_libbox.client.compat.DebugBackend;
import smile_libbox.client.compat.UiBackend;
import smile_libbox.client.compat.VersionedWorld;

public class SmileSLibBoxClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 使用方通过 Ui / Debug / World 门面访问 Minecraft；真正的实现由本 jar 按目标命名空间编译。
		// DebugBackend 现在只挂一个只读计数 tick，不再改写任何 KeyMapping（见 README「调试动作」）。
		Ui.install(new UiBackend());
		Debug.install(new DebugBackend());
		World.install(new VersionedWorld());
	}
}