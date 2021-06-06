/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.entrypoint.minecraft;

import java.io.IOException;
import java.util.ListIterator;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.quiltmc.loader.impl.entrypoint.EntrypointPatch;
import org.quiltmc.loader.impl.entrypoint.EntrypointTransformer;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;

public final class EntrypointPatchBranding extends EntrypointPatch {

	public EntrypointPatchBranding(EntrypointTransformer transformer) {
		super(transformer);
	}

	@Override
	public void process(QuiltLauncher launcher, Consumer<ClassNode> classEmitter) {
		for (String brandClassName : new String[] {
			"net.minecraft.client.ClientBrandRetriever",
			"net.minecraft.server.MinecraftServer",
		}) {
			try {
				ClassNode brandClass = loadClass(launcher, brandClassName);
				if (brandClass != null) {
					if (applyBrandingPatch(brandClass)) {
						classEmitter.accept(brandClass);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean applyBrandingPatch(ClassNode classNode) {
		boolean applied = false;

		for (MethodNode node : classNode.methods) {
			if (
				node.name.equals("getClientModName") ||
				node.name.equals("getServerModName") &&
				node.desc.endsWith(")Ljava/lang/String;")
			) {
				debug("Applying brand name hook to " + classNode.name + "::" + node.name);

				ListIterator<AbstractInsnNode> it = node.instructions.iterator();
				while (it.hasNext()) {
					if (it.next().getOpcode() == Opcodes.ARETURN) {
						it.previous();
						it.add(
							new MethodInsnNode(
								Opcodes.INVOKESTATIC,
								"org/quiltmc/loader/impl/entrypoint/minecraft/hooks/EntrypointBranding",
								"brand",
								"(Ljava/lang/String;)Ljava/lang/String;",
								false
							)
						);
						it.next();
					}
				}

				applied = true;
			}
		}

		return applied;
	}
}
