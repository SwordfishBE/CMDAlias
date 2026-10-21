package net.cmdalias;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public final class AliasCommandNode<S> extends LiteralCommandNode<S> {
	AliasCommandNode(LiteralCommandNode<S> node) {
		super(node.getLiteral(), node.getCommand(), node.getRequirement(), node.getRedirect(), node.getRedirectModifier(), node.isFork());
		node.getChildren().forEach(this::addChild);
	}

	public static <S> void prepareRegistration(CommandNode<S> parent, CommandNode<S> incoming) {
		if (parent instanceof RootCommandNode<S>) {
			CommandNode<S> existing = parent.getChild(incoming.getName());
			if (existing instanceof AliasCommandNode<S> && existing != incoming) {
				// Preserve the entire incoming registration instead of Brigadier merging it into our alias.
				remove(parent, existing);
			}
		}
	}

	static <S> void remove(CommandNode<S> parent, CommandNode<S> expected) {
		for (String fieldName : List.of("children", "literals", "arguments")) {
			try {
				Field field = CommandNode.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				@SuppressWarnings("unchecked")
				Map<String, CommandNode<S>> nodes = (Map<String, CommandNode<S>>) field.get(parent);
				nodes.remove(expected.getName(), expected);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Failed to remove alias command node '" + expected.getName() + "'", exception);
			}
		}
	}
}
