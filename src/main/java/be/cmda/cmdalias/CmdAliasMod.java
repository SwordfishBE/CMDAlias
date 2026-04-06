package be.cmda.cmdalias;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CmdAliasMod implements ModInitializer {
	public static final String MOD_ID = "cmdalias";
	public static final String MOD_NAME = "CmdAlias";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private final AliasManager aliasManager = new AliasManager();

	@Override
	public void onInitialize() {
		aliasManager.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> aliasManager.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(aliasManager::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> aliasManager.clearServer());
		LOGGER.info("[{}] Mod initialized. Version: {}", MOD_NAME, getVersion());
	}

	private String getVersion() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
			.getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}
}
