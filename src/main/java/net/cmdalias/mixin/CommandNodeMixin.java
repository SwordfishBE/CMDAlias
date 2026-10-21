package net.cmdalias.mixin;

import com.mojang.brigadier.tree.CommandNode;
import net.cmdalias.AliasCommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommandNode.class, remap = false)
abstract class CommandNodeMixin<S> {
	@SuppressWarnings("unchecked")
	@Inject(method = "addChild", at = @At("HEAD"))
	private void cmdalias$prepareRegistration(CommandNode<S> node, CallbackInfo callback) {
		AliasCommandNode.prepareRegistration((CommandNode<S>) (Object) this, node);
	}
}
