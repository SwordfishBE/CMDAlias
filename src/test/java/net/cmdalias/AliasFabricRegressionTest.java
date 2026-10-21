package net.cmdalias;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.util.Map;

public final class AliasFabricRegressionTest implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
		dispatcher.register(LiteralArgumentBuilder.<Integer>literal("target").executes(context -> 7));
		dispatcher.register(LiteralArgumentBuilder.<Integer>literal("execute")
			.then(LiteralArgumentBuilder.<Integer>literal("run").redirect(dispatcher.getRoot())));
		AliasCommandTree<Integer> tree = new AliasCommandTree<>((context, command) -> 1);
		tree.attach(dispatcher, Map.of("alias", "/target", "loop", "/execute run loop"));
		require(!dispatcher.getRoot().getChild("loop").canUse(3), "Recursive alias must be rejected");

		var nativeNode = dispatcher.register(LiteralArgumentBuilder.<Integer>literal("alias")
			.requires(level -> level >= 3)
			.executes(context -> 42)
			.then(LiteralArgumentBuilder.<Integer>literal("child").executes(context -> 99)));
		require(dispatcher.getRoot().getChild("alias") == nativeNode, "Mixin must replace the alias before merging");
		require(tree.hasConflict("alias"), "Native replacement must be recognized as a conflict");
		require(!nativeNode.canUse(2), "Native permissions must be preserved");
		require(nativeNode.getRedirect() == null, "Alias redirect must not survive replacement");
		tree.rebuild(Map.of());
		require(dispatcher.getRoot().getChild("alias") == nativeNode, "Native command must survive alias deletion");
		try {
			require(dispatcher.execute("alias", 3) == 42, "Native command must execute");
			require(dispatcher.execute("alias child", 3) == 99, "Native child must execute");
		} catch (Exception exception) {
			throw new AssertionError("Native registration failed", exception);
		}
		tree.clear();
		System.out.println("CMDAlias Fabric regression checks passed.");
		// This dedicated test JVM exits before Minecraft starts or opens a world.
		System.exit(0);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
