package net.cmdalias;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public final class AliasManager {
	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create();

	private final Path configPath;
	private final Map<String, String> aliases = new TreeMap<>();

	private final AliasCommandTree<CommandSourceStack> commandTree = new AliasCommandTree<>((context, command) -> {
		context.getSource().getServer().getCommands().performPrefixedCommand(context.getSource(), command);
		return 1;
	});
	private MinecraftServer server;

	public AliasManager() {
		this(FabricLoader.getInstance().getConfigDir().resolve(CmdAliasMod.MOD_ID + ".json"));
	}

	AliasManager(Path configPath) {
		this.configPath = configPath;
	}

	public void load() {
		aliases.clear();

		if (Files.notExists(configPath)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(configPath)) {
			StoredAliases storedAliases = GSON.fromJson(reader, StoredAliases.class);
			if (storedAliases == null || storedAliases.aliases == null) {
				return;
			}

			storedAliases.aliases.forEach((alias, command) -> {
				String normalizedAlias = normalizeAlias(alias);
				String normalizedCommand = normalizeCommand(command);
				if (normalizedAlias == null || normalizedCommand == null) {
					CmdAliasMod.LOGGER.warn("Skipping invalid alias entry '{}' from config", alias);
					return;
				}

				aliases.put(normalizedAlias, normalizedCommand);
			});
		} catch (IOException | JsonParseException exception) {
			CmdAliasMod.LOGGER.error("Failed to load alias config from {}", configPath, exception);
		}
	}

	public void save() {
		try {
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(configPath)) {
				GSON.toJson(new StoredAliases(new LinkedHashMap<>(aliases)), writer);
			}
		} catch (IOException exception) {
			CmdAliasMod.LOGGER.error("Failed to save alias config to {}", configPath, exception);
		}
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(buildAdminCommand());
		commandTree.attach(dispatcher, aliases);
	}

	public void setServer(MinecraftServer server) {
		CommandDispatcher<CommandSourceStack> currentDispatcher = server.getCommands().getDispatcher();
		this.server = server;
		if (!commandTree.isDispatcher(currentDispatcher)) {
			commandTree.attach(currentDispatcher, aliases);
		}
		syncCommands();
	}

	public void clearServer() {
		server = null;
		commandTree.clear();
	}

	private LiteralArgumentBuilder<CommandSourceStack> buildAdminCommand() {
		return Commands.literal("cmdalias")
			.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
			.then(Commands.literal("add")
				.then(Commands.argument("alias", StringArgumentType.word())
					.then(Commands.argument("command", StringArgumentType.greedyString())
						.executes(context -> addAlias(
							context,
							StringArgumentType.getString(context, "alias"),
							StringArgumentType.getString(context, "command")
						)))))
			.then(Commands.literal("del")
				.then(Commands.argument("alias", StringArgumentType.word())
					.suggests(this::suggestAliases)
					.executes(context -> deleteAlias(context, StringArgumentType.getString(context, "alias")))))
			.then(Commands.literal("list")
				.executes(this::listAliases));
	}

	private int addAlias(CommandContext<CommandSourceStack> context, String aliasInput, String commandInput) {
		if (!commandTree.isReady()) {
			context.getSource().sendFailure(Component.literal("Command dispatcher is not ready yet."));
			return 0;
		}

		String alias = normalizeAlias(aliasInput);
		if (alias == null) {
			context.getSource().sendFailure(Component.literal("Alias names may only contain lowercase letters, numbers, underscores, or dashes."));
			return 0;
		}

		String command = normalizeCommand(commandInput);
		if (command == null) {
			context.getSource().sendFailure(Component.literal("The target command cannot be empty."));
			return 0;
		}

		boolean replacingExistingAlias = aliases.containsKey(alias);
		if (commandTree.hasConflict(alias)) {
			context.getSource().sendFailure(Component.literal("Cannot register '/" + alias + "' because another command already uses that name."));
			return 0;
		}

		if (wouldCreateLoop(alias, command)) {
			context.getSource().sendFailure(Component.literal("That alias would create a loop."));
			return 0;
		}

		aliases.put(alias, command);
		commandTree.rebuild(aliases);
		save();
		syncCommands();

		String action = replacingExistingAlias ? "Updated" : "Added";
		context.getSource().sendSuccess(() -> Component.literal(action + " alias '/" + alias + "' -> '" + command + "'."), true);
		return 1;
	}

	private int deleteAlias(CommandContext<CommandSourceStack> context, String aliasInput) {
		String alias = normalizeAlias(aliasInput);
		if (alias == null || !aliases.containsKey(alias)) {
			context.getSource().sendFailure(Component.literal("Unknown alias: " + aliasInput));
			return 0;
		}

		aliases.remove(alias);
		commandTree.rebuild(aliases);
		save();
		syncCommands();

		context.getSource().sendSuccess(() -> Component.literal("Deleted alias '/" + alias + "'."), true);
		return 1;
	}

	private int listAliases(CommandContext<CommandSourceStack> context) {
		if (aliases.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("No aliases are configured."), false);
			return 1;
		}

		String joined = aliases.entrySet().stream()
			.map(entry -> "/" + entry.getKey() + " -> " + entry.getValue())
			.reduce((left, right) -> left + ", " + right)
			.orElse("");
		context.getSource().sendSuccess(() -> Component.literal(joined), false);
		return aliases.size();
	}

	private CompletableFuture<Suggestions> suggestAliases(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(aliases.keySet(), builder);
	}

	private void syncCommands() {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			server.getCommands().sendCommands(player);
		}
	}

	private boolean wouldCreateLoop(String alias, String command) {
		Map<String, String> testAliases = new TreeMap<>(aliases);
		testAliases.put(alias, command);
		String currentAlias = alias;
		String currentCommand = command;

		for (int i = 0; i <= testAliases.size(); i++) {
			String firstToken = extractFirstToken(currentCommand);
			if (firstToken == null) {
				return false;
			}

			if (firstToken.equals(currentAlias)) {
				return true;
			}

			currentCommand = testAliases.get(firstToken);
			if (currentCommand == null) {
				return false;
			}
		}

		return true;
	}

	private String extractFirstToken(String command) {
		String normalized = normalizeCommand(command);
		if (normalized == null) {
			return null;
		}

		String withoutSlash = normalized.substring(1);
		int spaceIndex = withoutSlash.indexOf(' ');
		return spaceIndex >= 0 ? withoutSlash.substring(0, spaceIndex) : withoutSlash;
	}

	private String normalizeAlias(String alias) {
		if (alias == null) {
			return null;
		}

		String normalized = alias.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || !normalized.matches("[a-z0-9_-]+")) {
			return null;
		}

		return normalized;
	}

	private String normalizeCommand(String command) {
		if (command == null) {
			return null;
		}

		String normalized = command.trim();
		if (normalized.isEmpty()) {
			return null;
		}

		return normalized.startsWith("/") ? normalized : "/" + normalized;
	}

	private static final class StoredAliases {
		@SerializedName("aliases")
		private final Map<String, String> aliases;

		private StoredAliases(Map<String, String> aliases) {
			this.aliases = aliases;
		}
	}
}
