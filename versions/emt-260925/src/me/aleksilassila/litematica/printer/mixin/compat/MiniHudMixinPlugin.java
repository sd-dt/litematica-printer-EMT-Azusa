package me.aleksilassila.litematica.printer.mixin.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 让 MiniHUD 兼容 mixin 变成"可选"：没装 MiniHUD 时整份配置里的 mixin 全部跳过，
 * 既不会因为找不到目标类而报错，也不会多加载一个引用 MiniHUD 的类。
 */
public class MiniHudMixinPlugin implements IMixinConfigPlugin {
   private static final boolean MINIHUD_LOADED = FabricLoader.getInstance().isModLoaded("minihud");

   @Override
   public void onLoad(String mixinPackage) {
   }

   @Override
   public String getRefMapperConfig() {
      return null;
   }

   @Override
   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return MINIHUD_LOADED;
   }

   @Override
   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   @Override
   public List<String> getMixins() {
      return null;
   }

   @Override
   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   @Override
   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
