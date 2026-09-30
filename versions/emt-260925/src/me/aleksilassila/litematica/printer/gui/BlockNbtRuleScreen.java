package me.aleksilassila.litematica.printer.gui;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiScrollBar;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.render.GuiContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import me.aleksilassila.litematica.printer.utils.BlockNbtRule;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

public class BlockNbtRuleScreen extends GuiBase {
   private static final int ROW_HEIGHT = 24;
   private static final int FIRST_ROW_Y = 45;
   private static final int VISIBLE_ROWS = 8;
   private static final int FIELD_X = 300;
   private static final int FIELD_WIDTH = 210;
   private final IConfigStringList config;
   private final int index;
   private final Screen parent;
   private final List<GuiTextFieldGeneric> values = new ArrayList<>();
   private final List<String> statePaths;
   private final String blockMatcher;
   private final GuiScrollBar scrollBar = new GuiScrollBar();
   private int scrollOffset;

   public BlockNbtRuleScreen(IConfigStringList config, int index, String currentEntry, Screen parent) {
      this.config = config;
      this.index = index;
      this.parent = parent;
      BlockNbtRule rule = BlockNbtRule.parse(currentEntry);
      this.blockMatcher = rule.blockMatcher();
      this.statePaths = this.findStatePaths(rule);
      this.title = MessageUtils.translatable("litematica_printer.gui.nbt.title", this.blockMatcher).getString();
      Map<String, String> oldValues = new LinkedHashMap<>();

      for (BlockNbtRule.Condition condition : rule.conditions()) {
         oldValues.put(condition.path(), condition.value());
      }

      for (String path : this.statePaths) {
         GuiTextFieldGeneric value = new GuiTextFieldGeneric(300, 45, 210, 20, this.font);
         value.setMaxLengthWrapper(512);
         value.setTextWrapper(oldValues.getOrDefault(path, ""));
         this.values.add(value);
      }

      this.setParent(parent);
      this.scrollBar.setMaxValue(Math.max(0, this.statePaths.size() - 8));
   }

   private List<String> findStatePaths(BlockNbtRule rule) {
      BlockState target = null;
      ClientLevel level = Minecraft.getInstance().level;
      LocalPlayer player = Minecraft.getInstance().player;
      if (level != null
         && player != null
         && Minecraft.getInstance().hitResult instanceof BlockHitResult hit
         && hit.getType() == Type.BLOCK
         && PlayerUtils.isWithinBlockInteractionRange(player, hit.getBlockPos(), 0.0)) {
         BlockState state = level.getBlockState(hit.getBlockPos());
         if (PinYinSearchUtils.matchBlockName(rule.blockMatcher(), state)) {
            target = state;
         }
      }

      if (target == null && level != null && player != null) {
         int range = (int)Math.ceil(PlayerUtils.getInteractionRange(5.0));
         BlockPos center = player.blockPosition();

         for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range, -range), center.offset(range, range, range))) {
            if (PlayerUtils.isWithinBlockInteractionRange(player, pos, 0.0)) {
               BlockState state = level.getBlockState(pos);
               if (PinYinSearchUtils.matchBlockName(rule.blockMatcher(), state)) {
                  target = state;
                  break;
               }
            }
         }
      }

      Set<String> paths = new LinkedHashSet<>();
      if (target != null) {
         for (Property<?> property : target.getProperties()) {
            paths.add("state." + property.getName());
         }
      }

      rule.conditions().stream().map(BlockNbtRule.Condition::path).filter(path -> path.startsWith("state.")).forEach(paths::add);
      return new ArrayList<>(paths);
   }

   public void initGui() {
      super.initGui();
      this.clearElements();
      this.addLabel(80, 25, 210, 20, -1, new String[]{MessageUtils.translatable("litematica_printer.gui.nbt.path").getString()});
      this.addLabel(300, 25, 210, 20, -1, new String[]{MessageUtils.translatable("litematica_printer.gui.nbt.value").getString()});

      for (GuiTextFieldGeneric value : this.values) {
         this.addTextField(value, new BlockNbtRuleScreen.ChangeListener());
      }
   }

   protected void drawContents(GuiContext context, int mouseX, int mouseY, float partialTicks) {
      this.scrollOffset = this.scrollBar.getValue();

      for (int i = 0; i < this.values.size(); i++) {
         int y = 45 + (i - this.scrollOffset) * 24;
         this.values.get(i).setYWrapper(y);
         if (i >= this.scrollOffset && i < this.scrollOffset + 8) {
            this.values.get(i).setXWrapper(300);
            this.drawString(context, this.statePaths.get(i), 80, y + 6, -1);
         } else {
            this.values.get(i).setXWrapper(-230);
         }
      }

      if (this.statePaths.size() > 8) {
         this.scrollBar.render(context, 520, 45, 14.0F, 192, 0, 0, 0, 0);
      }
   }

   public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.statePaths.size() > 8 && mouseX >= 70.0 && mouseX <= 540.0 && mouseY >= 45.0 && mouseY < 237.0) {
         this.scrollBar.offsetValue(verticalAmount < 0.0 ? 1 : -1);
         return true;
      } else {
         return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   private void updateConfig() {
      List<BlockNbtRule.Condition> conditions = new ArrayList<>();

      for (int i = 0; i < this.statePaths.size(); i++) {
         String value = this.values.get(i).getTextWrapper();
         if (!value.isBlank()) {
            conditions.add(new BlockNbtRule.Condition(this.statePaths.get(i), value));
         }
      }

      if (this.index >= 0 && this.index < this.config.getStrings().size()) {
         this.config.getStrings().set(this.index, new BlockNbtRule(this.blockMatcher, conditions).encode());
         this.config.markDirty();
         this.config.setModified();
      }
   }

   private class ChangeListener implements ITextFieldListener<GuiTextFieldGeneric> {
      private ChangeListener() {
         Objects.requireNonNull(BlockNbtRuleScreen.this);
         super();
      }

      public boolean onTextChange(GuiTextFieldGeneric textField) {
         BlockNbtRuleScreen.this.updateConfig();
         return true;
      }
   }
}
