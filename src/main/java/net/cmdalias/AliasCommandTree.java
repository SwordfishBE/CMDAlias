package net.cmdalias;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

final class AliasCommandTree<S> {
	private final ToIntBiFunction<CommandContext<S>, String> executor;
	private final Map<String, CommandNode<S>> registeredAliases = new HashMap<>();
	private final ThreadLocal<Set<String>> permissionChecks = new ThreadLocal<>();
	private CommandDispatcher<S> dispatcher;

	AliasCommandTree(ToIntBiFunction<CommandContext<S>, String> executor) {
		this.executor = executor;
	}

	void attach(CommandDispatcher<S> dispatcher, Map<String, String> aliases) {
		if (this.dispatcher != dispatcher) {
			clear();
		}
		this.dispatcher = dispatcher;
		rebuild(aliases);
	}

	boolean isDispatcher(CommandDispatcher<S> dispatcher) {
		return this.dispatcher == dispatcher;
	}

	boolean isReady() {
		return dispatcher != null;
	}

	boolean hasConflict(String alias) {
		CommandNode<S> existing = dispatcher.getRoot().getChild(alias);
		return existing != null && existing != registeredAliases.get(alias);
	}

	void clear() {
		dispatcher = null;
		registeredAliases.clear();
		permissionChecks.remove();
	}

	void rebuild(Map<String, String> aliases) {
		if (dispatcher == null) {
			return;
		}

		removeRegisteredAliases();
		Set<String> availableNames = new HashSet<>(aliases.keySet());
		availableNames.removeIf(this::hasConflict);
		for (String alias : availableNames) {
			String command = resolveTarget(alias, aliases, availableNames);
			if (command == null) {
				continue;
			}

			Target<S> target = findLiteralTarget(command);
			LiteralArgumentBuilder<S> builder = LiteralArgumentBuilder.<S>literal(alias)
				.requires(source -> canUseAlias(alias, command, target, source))
				.executes(context -> execute(context, command));
			if (target != null) {
				builder.redirect(target.node());
			} else {
				builder.then(RequiredArgumentBuilder.<S, String>argument("cmdalias_arguments", StringArgumentType.greedyString())
					.executes(context -> execute(context, command)));
			}
			CommandNode<S> node = new AliasCommandNode<>(builder.build());
			dispatcher.getRoot().addChild(node);
			registeredAliases.put(alias, node);
		}
	}

	private int execute(CommandContext<S> context, String command) {
		// Redirected contexts retain the full input, so use the alias node's actual range.
		int aliasEnd = context.getNodes().getFirst().getRange().getEnd();
		String suffix = context.getInput().substring(aliasEnd, context.getRange().getEnd()).trim();
		return executor.applyAsInt(context, suffix.isEmpty() ? command : command + " " + suffix);
	}

	private String resolveTarget(String alias, Map<String, String> aliases, Set<String> availableNames) {
		Set<String> visited = new HashSet<>();
		visited.add(alias);
		String command = withoutSlash(aliases.get(alias));
		while (true) {
			int separator = command.indexOf(' ');
			String name = separator < 0 ? command : command.substring(0, separator);
			if (!availableNames.contains(name)) {
				return command;
			}
			if (!visited.add(name)) {
				return null;
			}
			command = withoutSlash(aliases.get(name)) + (separator < 0 ? "" : command.substring(separator));
		}
	}

	private Target<S> findLiteralTarget(String command) {
		CommandNode<S> current = dispatcher.getRoot();
		List<Predicate<S>> requirements = new ArrayList<>();
		for (String token : command.split(" +")) {
			current = current.getChild(token);
			if (!(current instanceof LiteralCommandNode<S>)) {
				return null;
			}
			requirements.add(current.getRequirement());
			Set<CommandNode<S>> redirects = new HashSet<>();
			while (current.getRedirect() != null) {
				if (current.getRedirectModifier() != null || current.isFork()) {
					return null;
				}
				if (!redirects.add(current)) {
					return null;
				}
				current = current.getRedirect();
				requirements.add(current.getRequirement());
			}
		}
		return new Target<>(current, source -> requirements.stream().allMatch(requirement -> requirement.test(source)));
	}

	private boolean canUseTarget(String command, S source) {
		var parsed = dispatcher.parse(command, source);
		return !parsed.getReader().canRead() && !parsed.getContext().getNodes().isEmpty();
	}

	private boolean canUseAlias(String alias, String command, Target<S> target, S source) {
		Set<String> active = permissionChecks.get();
		if (active == null) {
			active = new HashSet<>();
			permissionChecks.set(active);
		}
		if (!active.add(alias)) {
			return false;
		}
		try {
			return target == null ? canUseTarget(command, source) : target.requirement().test(source);
		} finally {
			active.remove(alias);
			if (active.isEmpty()) {
				permissionChecks.remove();
			}
		}
	}

	private void removeRegisteredAliases() {
		registeredAliases.values().forEach(node -> AliasCommandNode.remove(dispatcher.getRoot(), node));
		registeredAliases.clear();
	}

	private static String withoutSlash(String command) {
		return command.startsWith("/") ? command.substring(1) : command;
	}

	private record Target<S>(CommandNode<S> node, Predicate<S> requirement) {
	}
}
