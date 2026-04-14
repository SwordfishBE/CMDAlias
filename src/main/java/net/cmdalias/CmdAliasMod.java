package net.cmdalias;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CmdAliasMod implements ModInitializer {
	public static final String MOD_ID = "cmdalias";
	private static final ModContainer MOD_CONTAINER = FabricLoader.getInstance()
		.getModContainer(MOD_ID)
		.orElseThrow(() -> new IllegalStateException("Missing mod metadata for " + MOD_ID));
	public static final String MOD_NAME = MOD_CONTAINER.getMetadata().getName();
	public static final String MOD_VERSION = MOD_CONTAINER.getMetadata().getVersion().getFriendlyString();
	public static final String LOG_PREFIX = "[" + MOD_NAME + "]";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final AliasManager aliasManager = new AliasManager();

	@Override
	public void onInitialize() {
		aliasManager.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> aliasManager.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(aliasManager::setServer);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> UpdateChecker.checkForUpdates());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> aliasManager.clearServer());
		LOGGER.info("{} Mod initialized. Version: {}", LOG_PREFIX, MOD_VERSION);
	}
}
