package net.cmdalias;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AliasCommandTreeTest {
	private static final Source PLAYER = new Source(0);
	private static final Source OP = new Source(2);
	private static final Source ADMIN = new Source(3);
	private final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>(new RegistrationRoot<>());
	private final Map<String, String> aliases = new LinkedHashMap<>();
	private final List<String> forwarded = new ArrayList<>();
	private final List<String> executed = new ArrayList<>();
	private final AliasCommandTree<Source> tree = new AliasCommandTree<>((context, command) -> {
		forwarded.add(command);
		try {
			return dispatcher.execute(command, context.getSource());
		} catch (CommandSyntaxException exception) {
			throw new AssertionError("Forwarded invalid command: " + command, exception);
		}
	});

	@BeforeEach
	void registerCommands() {
		dispatcher.register(literal("weather").requires(source -> source.level() >= 2)
			.then(literal("clear").executes(context -> record("weather clear", 7))
				.then(RequiredArgumentBuilder.<Source, Integer>argument("duration", IntegerArgumentType.integer())
					.executes(context -> record("weather clear " + IntegerArgumentType.getInteger(context, "duration"), 7)))));
		var teleport = dispatcher.register(literal("teleport").requires(source -> source.level() >= 2)
			.then(RequiredArgumentBuilder.<Source, String>argument("location", StringArgumentType.greedyString())
				.suggests((context, builder) -> builder.suggest("0 64 0").buildFuture())
				.executes(context -> record("tp " + StringArgumentType.getString(context, "location"), 3))));
		dispatcher.register(literal("tp").requires(source -> source.level() >= 2).redirect(teleport));
		dispatcher.register(literal("gamemode").requires(source -> source.level() >= 3)
			.then(literal("creative").executes(context -> record("gamemode creative", 2))));
		dispatcher.register(literal("execute").then(literal("run").redirect(dispatcher.getRoot())));
	}

	@Test
	void forwardsBareAliasInsideExecuteUsingItsActualInputRange() throws Exception {
		attach("sun", "/weather clear");
		assertEquals(7, dispatcher.execute("sun", OP));
		assertEquals(7, dispatcher.execute("execute run sun", OP));
		assertEquals(List.of("weather clear", "weather clear"), forwarded);
		assertEquals(forwarded, executed);
	}

	@Test
	void redirectsArgumentsToOriginalCommandInsideExecute() throws Exception {
		attach("sun", "/weather clear");
		assertEquals(7, dispatcher.execute("execute run sun 60", OP));
		assertEquals(List.of("weather clear 60"), executed);
	}

	@Test
	void followsTeleportRedirectAndPreservesSuggestions() throws Exception {
		attach("t", "/tp");
		assertEquals(3, dispatcher.execute("t 0 64 0", OP));
		assertEquals(3, dispatcher.execute("execute run t 1 65 1", OP));
		var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("t ", OP)).get();
		assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("0 64 0")));
		assertEquals(List.of("tp 0 64 0", "tp 1 65 1"), executed);
	}

	@Test
	void forwardsFixedArgumentsInsideExecuteAndAppendsExtraArguments() throws Exception {
		attach("spawn", "/tp 0 64 0");
		assertEquals(3, dispatcher.execute("execute run spawn", OP));
		assertEquals(3, dispatcher.execute("execute run spawn facing 1 2 3", OP));
		assertEquals(List.of("tp 0 64 0", "tp 0 64 0 facing 1 2 3"), forwarded);
		assertEquals(forwarded, executed);
	}

	@Test
	void preservesConflictingNativeCommandWhenAliasIsUpdatedOrDeleted() throws Exception {
		var nativeNode = dispatcher.register(literal("spawn").executes(context -> 42));
		attach("spawn", "/weather clear");
		assertTrue(tree.hasConflict("spawn"));
		aliases.put("spawn", "/gamemode creative");
		tree.rebuild(aliases);
		assertSame(nativeNode, dispatcher.getRoot().getChild("spawn"));
		aliases.remove("spawn");
		tree.rebuild(aliases);
		assertSame(nativeNode, dispatcher.getRoot().getChild("spawn"));
		assertEquals(42, dispatcher.execute("spawn", PLAYER));
	}

	@Test
	void releasesAllOwnedNodeAndDispatcherReferencesOnClear() throws Exception {
		attach("sun", "/weather clear");
		tree.clear();
		assertFalse(tree.isReady());
		assertNull(readField(tree, "dispatcher"));
		assertTrue(((Map<?, ?>) readField(tree, "registeredAliases")).isEmpty());
	}

	@Test
	void serverStopClearsManagersCommandTree(@TempDir Path configDir) throws Exception {
		AliasManager manager = new AliasManager(configDir.resolve("cmdalias.json"));
		@SuppressWarnings("unchecked")
		AliasCommandTree<Object> managerTree = (AliasCommandTree<Object>) readField(manager, "commandTree");
		managerTree.attach(new CommandDispatcher<>(), Map.of("unknown", "/missing"));
		assertTrue(managerTree.isReady());
		manager.clearServer();
		assertNull(readField(manager, "server"));
		assertNull(readField(managerTree, "dispatcher"));
		assertTrue(((Map<?, ?>) readField(managerTree, "registeredAliases")).isEmpty());
	}

	@Test
	void hidesAndRejectsFixedArgumentAliasWithoutTargetPermission() throws Exception {
		attach("spawn", "/tp 0 64 0");
		assertFalse(dispatcher.getRoot().getChild("spawn").canUse(PLAYER));
		assertTrue(dispatcher.getRoot().getChild("spawn").canUse(OP));
		assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("spawn", PLAYER));
		assertEquals(3, dispatcher.execute("spawn", OP));
	}

	@Test
	void preservesEveryParentPermissionForLiteralAndFixedArgumentTargets() {
		dispatcher.register(literal("staff").requires(source -> source.level() >= 3)
			.then(literal("action").then(RequiredArgumentBuilder.<Source, Integer>argument("value", IntegerArgumentType.integer())
				.executes(context -> 1))));
		aliases.put("literal", "/staff action");
		aliases.put("fixed", "/staff action 1");
		tree.attach(dispatcher, aliases);
		for (String name : aliases.keySet()) {
			assertFalse(dispatcher.getRoot().getChild(name).canUse(OP));
			assertTrue(dispatcher.getRoot().getChild(name).canUse(ADMIN));
		}
	}

	@Test
	void rebuildsDependentAliasSyntaxAndPermissionsAfterTargetChanges() throws Exception {
		aliases.put("a", "/b");
		aliases.put("b", "/weather clear");
		tree.attach(dispatcher, aliases);
		assertEquals(7, dispatcher.execute("a 60", OP));
		aliases.put("b", "/gamemode");
		tree.rebuild(aliases);
		assertFalse(dispatcher.getRoot().getChild("a").canUse(OP));
		assertEquals(2, dispatcher.execute("a creative", ADMIN));
		assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("a 60", ADMIN));
		assertEquals(List.of("weather clear 60", "gamemode creative"), executed);
	}

	@Test
	void preservesFixedArgumentsAcrossAliasChainsRegardlessOfRegistrationOrder() throws Exception {
		aliases.put("a", "/b 0 64 0");
		aliases.put("b", "/tp");
		tree.attach(dispatcher, aliases);
		assertEquals(3, dispatcher.execute("a facing 1 2 3", OP));
		assertEquals(List.of("tp 0 64 0 facing 1 2 3"), executed);
	}

	@Test
	void reattachesOnSameDispatcherWithoutLosingOwnership() throws Exception {
		attach("sun", "/weather clear");
		tree.attach(dispatcher, aliases);
		assertFalse(tree.hasConflict("sun"));
		aliases.clear();
		tree.rebuild(aliases);
		assertNull(dispatcher.getRoot().getChild("sun"));
	}

	@Test
	void registersAliasesOnFreshDispatcherAfterReload() throws Exception {
		attach("sun", "/weather clear");
		CommandDispatcher<Source> fresh = new CommandDispatcher<>();
		fresh.register(literal("weather").then(literal("clear").then(literal("now").executes(context -> 99))));
		tree.attach(fresh, aliases);
		assertEquals(99, fresh.execute("sun now", OP));
		aliases.clear();
		tree.rebuild(aliases);
		assertNull(fresh.getRoot().getChild("sun"));
	}

	@Test
	void doesNotRegisterCyclesLoadedFromConfiguration() {
		aliases.put("a", "/b");
		aliases.put("b", "/a");
		tree.attach(dispatcher, aliases);
		assertNull(dispatcher.getRoot().getChild("a"));
		assertNull(dispatcher.getRoot().getChild("b"));
	}

	@Test
	void doesNotInterpretArgumentNamesAsLiteralValues() throws Exception {
		attach("place", "/tp location");
		assertEquals(3, dispatcher.execute("place", OP));
		assertEquals(List.of("tp location"), executed);
	}

	@Test
	void preservesSourceModifiersOnRedirectedTargets() throws Exception {
		dispatcher.register(literal("elevated").redirect(dispatcher.getRoot().getChild("gamemode"), context -> ADMIN));
		attach("mode", "/elevated");
		assertEquals(2, dispatcher.execute("mode creative", PLAYER));
		assertEquals(List.of("gamemode creative"), executed);
	}

	@Test
	void rejectsSelfReferenceInsideExecuteWithoutOverflowingPermissionChecks() throws Exception {
		attach("a", "/execute run a");
		assertFalse(dispatcher.getRoot().getChild("a").canUse(OP));
		assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("a", OP));
		assertNull(((ThreadLocal<?>) readField(tree, "permissionChecks")).get());
	}

	@Test
	void rejectsIndirectExecuteCyclesAndAllowsIndependentAliases() throws Exception {
		aliases.put("a", "/execute run b");
		aliases.put("b", "/execute run a");
		aliases.put("sun", "/weather clear");
		tree.attach(dispatcher, aliases);
		assertFalse(dispatcher.getRoot().getChild("a").canUse(OP));
		assertFalse(dispatcher.getRoot().getChild("b").canUse(OP));
		assertEquals(7, dispatcher.execute("sun", OP));
	}

	@Test
	void allowsNonCyclicExecuteAliasToAnotherAlias() throws Exception {
		aliases.put("a", "/execute run sun");
		aliases.put("sun", "/weather clear");
		tree.attach(dispatcher, aliases);
		assertEquals(7, dispatcher.execute("a", OP));
		assertFalse(dispatcher.getRoot().getChild("a").canUse(PLAYER));
	}

	@Test
	void clearsPermissionGuardEvenWhenTargetRequirementThrows() throws Exception {
		var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
		dispatcher.register(literal("flaky").requires(source -> {
			if (failOnce.getAndSet(false)) {
				throw new IllegalStateException("Simulated permission provider failure");
			}
			return true;
		}).executes(context -> 1));
		attach("a", "/flaky");
		assertThrows(IllegalStateException.class, () -> dispatcher.getRoot().getChild("a").canUse(OP));
		assertNull(((ThreadLocal<?>) readField(tree, "permissionChecks")).get());
		assertTrue(dispatcher.getRoot().getChild("a").canUse(OP));
	}

	@Test
	void preservesLaterNativeCommandAndItsPermissionsAcrossRebuildAndDeletion() throws Exception {
		attach("spawn", "/weather clear");
		var nativeNode = dispatcher.register(literal("spawn").requires(source -> source.level() >= 3)
			.executes(context -> 42));
		assertSame(nativeNode, dispatcher.getRoot().getChild("spawn"));
		assertTrue(tree.hasConflict("spawn"));
		assertFalse(nativeNode.canUse(OP));
		assertEquals(42, dispatcher.execute("spawn", ADMIN));
		tree.rebuild(aliases);
		assertSame(nativeNode, dispatcher.getRoot().getChild("spawn"));
		aliases.remove("spawn");
		tree.rebuild(aliases);
		assertSame(nativeNode, dispatcher.getRoot().getChild("spawn"));
		assertEquals(42, dispatcher.execute("spawn", ADMIN));
	}

	@Test
	void preservesLaterNativeChildrenAndRemovesAliasRedirect() throws Exception {
		attach("spawn", "/weather clear");
		dispatcher.register(literal("spawn").then(literal("native").executes(context -> 42)));
		assertNull(dispatcher.getRoot().getChild("spawn").getRedirect());
		assertEquals(42, dispatcher.execute("spawn native", PLAYER));
		aliases.clear();
		tree.rebuild(aliases);
		assertEquals(42, dispatcher.execute("spawn native", PLAYER));
		assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("spawn 60", OP));
	}

	@Test
	void laterNativeCommandDoesNotInheritFallbackGreedyArgument() throws Exception {
		attach("spawn", "/tp 0 64 0");
		dispatcher.register(literal("spawn").executes(context -> 42));
		assertTrue(dispatcher.getRoot().getChild("spawn").getChildren().isEmpty());
		assertEquals(42, dispatcher.execute("spawn", PLAYER));
		aliases.clear();
		tree.rebuild(aliases);
		assertEquals(42, dispatcher.execute("spawn", PLAYER));
	}

	private void attach(String alias, String command) {
		aliases.put(alias, command);
		tree.attach(dispatcher, aliases);
	}

	private int record(String command, int result) {
		executed.add(command);
		return result;
	}

	private static LiteralArgumentBuilder<Source> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private static Object readField(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private record Source(int level) {
	}

	// Unit tests call the same production hook that CommandNodeMixin installs in Fabric.
	private static final class RegistrationRoot<S> extends RootCommandNode<S> {
		@Override
		public void addChild(CommandNode<S> node) {
			AliasCommandNode.prepareRegistration(this, node);
			super.addChild(node);
		}
	}
}
