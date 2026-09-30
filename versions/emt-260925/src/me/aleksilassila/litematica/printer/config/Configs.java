package me.aleksilassila.litematica.printer.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.hotkeys.KeybindSettings.Context;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import fi.dy.masa.malilib.util.restrictions.UsageRestriction.ListType;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.enums.DefaultPlaceDirectionType;
import me.aleksilassila.litematica.printer.enums.EatMode;
import me.aleksilassila.litematica.printer.enums.ExcavateListMode;
import me.aleksilassila.litematica.printer.enums.MineSelectionType;
import me.aleksilassila.litematica.printer.enums.FillBlockModeType;
import me.aleksilassila.litematica.printer.enums.FillModeFacingType;
import me.aleksilassila.litematica.printer.enums.FluidAvoidStrategyType;
import me.aleksilassila.litematica.printer.enums.IterationOrderType;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.enums.RadiusShapeType;
import me.aleksilassila.litematica.printer.enums.SectionScanOrderType;
import me.aleksilassila.litematica.printer.enums.SelectionType;
import me.aleksilassila.litematica.printer.enums.WorkingModeType;
import me.aleksilassila.litematica.printer.gui.ConfigUi;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;

public class Configs extends ConfigBuilders implements IConfigHandler {
   private static final Configs INSTANCE = new Configs();
   private static final String FILE_PATH = "./config/litematica_printer.json";
   private static final File CONFIG_DIR = new File("./config");
   private static final KeybindSettings GUI_NO_ORDER = KeybindSettings.create(Context.GUI, KeyAction.PRESS, false, false, false, true);
   private static final BooleanSupplier isLoadCloudStoreLoaded = ModUtils::isCloudStoreLoaded;
   private static final BooleanSupplier isSingle = () -> Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.SINGLE);
   private static final BooleanSupplier isMulti = () -> Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI);
   private static final BooleanSupplier isBreakCustom = () -> Configs.Mine.BREAK_LIMITER.getOptionListValue().equals(ExcavateListMode.CUSTOM);
   private static final BooleanSupplier isBreakWhitelist = () -> isBreakCustom.getAsBoolean()
         && Configs.Mine.BREAK_LIMIT.getOptionListValue().equals(ListType.WHITELIST);
   private static final BooleanSupplier isBreakBlacklist = () -> isBreakCustom.getAsBoolean()
         && Configs.Mine.BREAK_LIMIT.getOptionListValue().equals(ListType.BLACKLIST);
   private static final BooleanSupplier isExcavateCustom = () -> Configs.Mine.EXCAVATE_LIMITER.getOptionListValue().equals(ExcavateListMode.CUSTOM);
   private static final BooleanSupplier isExcavateWhitelist = () -> isExcavateCustom.getAsBoolean()
         && Configs.Mine.EXCAVATE_LIMIT.getOptionListValue().equals(ListType.WHITELIST);
   private static final BooleanSupplier isExcavateBlacklist = () -> isExcavateCustom.getAsBoolean()
         && Configs.Mine.EXCAVATE_LIMIT.getOptionListValue().equals(ListType.BLACKLIST);
   private static final BooleanSupplier isBlocklist = () -> Configs.Fill.FILL_BLOCK_MODE.getOptionListValue().equals(FillBlockModeType.BLOCKLIST);
   public static final ImmutableList<IConfigBase> OPTIONS;
   public static final ImmutableList<IHotkey> HOTKEYS;

   public void load() {
      File settingFile = new File("./config/litematica_printer.json");
      if (settingFile.isFile() && settingFile.exists()) {
         JsonElement jsonElement = JsonUtils.parseJsonFile(settingFile.toPath());
         if (jsonElement != null && jsonElement.isJsonObject()) {
            JsonObject obj = jsonElement.getAsJsonObject();
            migrateSplitHotkeyBooleans(obj);
            ConfigUtils.readConfigBase(obj, "litematica_printer", OPTIONS);
            if (hasUnknownConfigKeys(obj)) {
               this.save();
            }
         }
      }
   }

   private static Set<String> registeredConfigKeys() {
      JsonObject tempRoot = new JsonObject();
      ConfigUtils.writeConfigBase(tempRoot, "litematica_printer", OPTIONS);
      return (Set<String>)(tempRoot.get("litematica_printer") instanceof JsonObject tempMod ? new HashSet<>(tempMod.keySet()) : Set.of());
   }

   private static boolean hasUnknownConfigKeys(JsonObject configRoot) {
      if (configRoot.get("litematica_printer") instanceof JsonObject modObject) {
         Set<String> registeredKeys = registeredConfigKeys();
         if (registeredKeys.isEmpty()) {
            return false;
         } else {
            for (String key : modObject.keySet()) {
               if (!registeredKeys.contains(key)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public void save() {
      if (CONFIG_DIR.exists() && CONFIG_DIR.isDirectory() || CONFIG_DIR.mkdirs()) {
         JsonObject configRoot = new JsonObject();
         ConfigUtils.writeConfigBase(configRoot, "litematica_printer", OPTIONS);
         JsonUtils.writeJsonToFile(configRoot, new File("./config/litematica_printer.json").toPath());
      }
   }

   private static void migrateSplitHotkeyBooleans(JsonObject obj) {
      if (obj.get("litematica_printer") instanceof JsonObject mod) {
         for (String key : List.of("workingSwitch", "print", "mine", "fill", "fluid", "printIceForWater")) {
            JsonElement var5 = mod.get(key);
            if (var5 instanceof JsonObject) {
               JsonObject old = (JsonObject)var5;
               if (old.has("enabled")) {
                  mod.addProperty(key, old.get("enabled").getAsBoolean());
                  if (old.has("hotkey")) {
                     mod.add(key + "Hotkey", old.get("hotkey"));
                  }
               }
            }
         }
      }
   }

   private static boolean toggleWithNotify(ConfigBoolean config) {
      config.toggleBooleanValue();
      boolean newValue = config.getBooleanValue();
      String pre = newValue ? GuiBase.TXT_GREEN : GuiBase.TXT_RED;
      I18n statusI18n = newValue ? I18n.MESSAGE_VALUE_ON : I18n.MESSAGE_VALUE_OFF;
      MutableComponent message = I18n.MESSAGE_TOGGLED.getName(config.getPrettyName(), pre + statusI18n.getName().getString() + GuiBase.TXT_RST);
      MessageUtils.setOverlayMessage(message);
      return true;
   }

   public static void init() {
      INSTANCE.load();
      ConfigManager.getInstance().registerConfigHandler("litematica_printer", INSTANCE);
      InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
      InputEventHandler.getInputManager().registerKeyboardInputHandler(InputHandler.getInstance());
      Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo("litematica_printer", "Litematica Printer", ConfigUi::new));
   }

   static {
      LinkedHashSet<IConfigBase> optionSet = new LinkedHashSet<>();
      optionSet.addAll(Configs.Core.OPTIONS);
      optionSet.addAll(Configs.Special.OPTIONS);
      optionSet.addAll(Configs.Go.OPTIONS);
      optionSet.addAll(Configs.Danger.OPTIONS);
      optionSet.addAll(Configs.Hotkeys.OPTIONS);
      optionSet.addAll(Configs.Print.OPTIONS);
      optionSet.addAll(Configs.Mine.OPTIONS);
      optionSet.addAll(Configs.Bedrock.OPTIONS);
      optionSet.addAll(Configs.Fill.OPTIONS);
      optionSet.addAll(Configs.Fluid.OPTIONS);
      OPTIONS = ImmutableList.copyOf(optionSet);
      List<IHotkey> hotkeys = new ArrayList<>();

      for (IConfigBase option : optionSet) {
         if (option instanceof IHotkey hokey) {
            hotkeys.add(hokey);
         }
      }

      HOTKEYS = ImmutableList.copyOf(hotkeys);
   }

   public static class Core {
      public static final ConfigBoolean WORK_SWITCH = ConfigBuilders.bool("workingSwitch").defaultValue(false).build();
      public static final ConfigBoolean HUD_WORK_STATUS = ConfigBuilders.bool("hudWorkStatus").defaultValue(true).build();
      public static final ConfigOptionList WORK_MODE = ConfigBuilders.optionList("modeSwitch").defaultValue(WorkingModeType.SINGLE).build();
      public static final ConfigBoolean PRINT = ConfigBuilders.bool("print").defaultValue(false).setVisible(Configs.isMulti).build();
      public static final ConfigBoolean MINE = ConfigBuilders.bool("mine").defaultValue(false).setVisible(Configs.isMulti).build();
      public static final ConfigBoolean FILL = ConfigBuilders.bool("fill").defaultValue(false).setVisible(Configs.isMulti).build();
      public static final ConfigBoolean FLUID = ConfigBuilders.bool("fluid").defaultValue(false).setVisible(Configs.isMulti).build();
      public static final ConfigOptionList WORK_MODE_TYPE = ConfigBuilders.optionList("printerMode")
         .defaultValue(PrintModeType.PRINTER)
         .setVisible(Configs.isSingle)
         .build();
      public static final ConfigInteger WORK_RANGE = ConfigBuilders.integer("workRange").defaultValue(6).range(1, 256).build();
      public static final ConfigInteger ITERATION_TIME_LIMIT = ConfigBuilders.integer("iterationTimeLimit").defaultValue(8).range(0, 32).build();
      public static final ConfigBoolean VERIFIER_OPTIMIZED = ConfigBuilders.bool("verifierOptimized").defaultValue(false).build();
      public static final ConfigBoolean CHECK_PLAYER_INTERACTION_RANGE = ConfigBuilders.bool("checkPlayerInteractionRange").defaultValue(true).build();
      public static final ConfigBoolean LAG_CHECK = ConfigBuilders.bool("printerLagCheck").defaultValue(true).build();
      public static final ConfigInteger LAG_CHECK_MAX = ConfigBuilders.integer("printerLagCheckMax")
         .defaultValue(20)
         .setVisible(LAG_CHECK::getBooleanValue)
         .range(20, 1200)
         .build();
      public static final ConfigOptionList ITERATOR_SHAPE = ConfigBuilders.optionList("printerIteratorShape").defaultValue(RadiusShapeType.SPHERE).build();
      public static final ConfigOptionList ITERATION_ORDER = ConfigBuilders.optionList("printerIteratorMode").defaultValue(IterationOrderType.XZY).build();
      public static final ConfigBoolean X_REVERSE = ConfigBuilders.bool("printerXAxisReverse").defaultValue(false).build();
      public static final ConfigBoolean Y_REVERSE = ConfigBuilders.bool("printerYAxisReverse").defaultValue(false).build();
      public static final ConfigBoolean Z_REVERSE = ConfigBuilders.bool("printerZAxisReverse").defaultValue(false).build();
      public static final ConfigBoolean MOVE_ADAPTIVE_ITERATION = ConfigBuilders.bool("moveAdaptiveIteration").defaultValue(true).build();
      public static final ConfigInteger MOTION_AHEAD = ConfigBuilders.integer("motionAheadBlocks").defaultValue(3).range(0, 16).build();
      public static final ConfigBoolean RENDER_HUD = ConfigBuilders.bool("renderHud").defaultValue(false).build();
      public static final ConfigBoolean AUTO_DISABLE_PRINTER = ConfigBuilders.bool("printerAutoDisable").defaultValue(true).build();
      public static final ConfigBoolean AUTO_ENABLE_PRINTER = ConfigBuilders.bool("printerAutoEnable").defaultValue(false).build();
      public static final ConfigBoolean DEBUG_OUTPUT = ConfigBuilders.bool("debugOutput").defaultValue(false).build();
      public static final ConfigBoolean HAND_RESTOCK_SHULKER_COMPAT = ConfigBuilders.bool("handRestockShulkerCompat").defaultValue(false).build();
      public static final ConfigBoolean QUICK_SHULKER = ConfigBuilders.bool("quickShulker").defaultValue(false).build();
      public static final ConfigInteger QUICK_SHULKER_MAX_STACKS = ConfigBuilders.integer("quickShulkerMaxStacks").defaultValue(1).range(1, 27).build();
      public static final ConfigInteger QUICK_SHULKER_COOLDOWN = ConfigBuilders.integer("quickShulkerCooldown").defaultValue(10).range(0, 20).build();
public static final ConfigBoolean AUTO_PACKET_LIMIT = ConfigBuilders.bool("autoPacketLimit").defaultValue(false).build();
      /**
       * 「重置发包上限」按钮：在配置界面里就是一个可点的 ON/OFF 按钮（malilib 没有独立的按钮型配置项），
       * 点一下（切成"开"）就清空所有服务器学到的发包上限记录，然后立刻弹回"关"，方便再点。
       * 具体逻辑接在 {@code InitHandler.initConfigCallback()} 里。
       */
      public static final ConfigBoolean RESET_PACKET_LIMIT = ConfigBuilders.bool("resetPacketLimit").defaultValue(false).build();
      public static final ConfigBoolean AUTO_TOOL_SWITCH = ConfigBuilders.bool("autoToolSwitch").defaultValue(true).build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
         WORK_SWITCH,
         AUTO_TOOL_SWITCH,
         HUD_WORK_STATUS,
         AUTO_PACKET_LIMIT,
         RESET_PACKET_LIMIT,
         WORK_MODE,
         WORK_MODE_TYPE,
         PRINT,
         MINE,
         FILL,
         FLUID,
         WORK_RANGE,
         ITERATION_TIME_LIMIT,
         VERIFIER_OPTIMIZED,
         RENDER_HUD,
         LAG_CHECK,
         LAG_CHECK_MAX,
         CHECK_PLAYER_INTERACTION_RANGE,
         ITERATOR_SHAPE,
         ITERATION_ORDER,
         X_REVERSE,
         Y_REVERSE,
         Z_REVERSE,
         MOVE_ADAPTIVE_ITERATION,
         MOTION_AHEAD,
         AUTO_DISABLE_PRINTER,
         AUTO_ENABLE_PRINTER,
         DEBUG_OUTPUT,
         HAND_RESTOCK_SHULKER_COMPAT,
         QUICK_SHULKER,
         QUICK_SHULKER_MAX_STACKS,
         QUICK_SHULKER_COOLDOWN
      );
   }

   public static class Danger {
      public static final ConfigBoolean SIMIAO = ConfigBuilders.bool("simiao").defaultValue(false).build();
      public static final ConfigBoolean DONOTCLICK_A = ConfigBuilders.bool("donotclickA").defaultValue(false).addValueChangeListener(config -> {
         if (((ConfigBoolean)config).getBooleanValue()) {
            ModUtils.openTrollVideoUrl();
            ((ConfigBoolean)config).setBooleanValue(false);
         }
      }).build();
      public static final ConfigBoolean DONOTCLICK_B = ConfigBuilders.bool("donotclickB").defaultValue(false).addValueChangeListener(config -> {
         if (((ConfigBoolean)config).getBooleanValue()) {
            ModUtils.playBundledVideo();
            ((ConfigBoolean)config).setBooleanValue(false);
         }
      }).build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(SIMIAO, DONOTCLICK_A, DONOTCLICK_B);
   }

   public static class Fill {
      public static final ConfigOptionList FILL_SELECTION_TYPE = ConfigBuilders.optionList("fillSelectionType")
         .defaultValue(SelectionType.LITEMATICA_SELECTION)
         .build();
      public static final ConfigOptionList FILL_BLOCK_MODE = ConfigBuilders.optionList("fillBlockMode").defaultValue(FillBlockModeType.BLOCKLIST).build();
      public static final ConfigStringList FILL_BLOCK_LIST = ConfigBuilders.stringList("fillBlockList")
         .defaultValue(Blocks.COBBLESTONE)
         .setVisible(Configs.isBlocklist)
         .build();
      public static final ConfigOptionList FILL_BLOCK_FACING = ConfigBuilders.optionList("fillModeFacing").defaultValue(FillModeFacingType.NONE).build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(FILL_SELECTION_TYPE, FILL_BLOCK_MODE, FILL_BLOCK_LIST, FILL_BLOCK_FACING);
   }

   public static class Fluid {
      public static final ConfigOptionList FLUID_SELECTION_TYPE = ConfigBuilders.optionList("fluidSelectionType")
         .defaultValue(SelectionType.LITEMATICA_SELECTION)
         .build();
      public static final ConfigBoolean FILL_FLOWING_FLUID = ConfigBuilders.bool("fluidModeFillFlowing").defaultValue(true).build();
      public static final ConfigStringList FLUID_REPLACE_BLOCK_LIST = ConfigBuilders.stringList("fluidReplaceBlockList").defaultValue(Blocks.COBBLESTONE).build();
      public static final ConfigStringList FLUID_LIST = ConfigBuilders.stringList("fluidList").defaultValue(Blocks.WATER, Blocks.LAVA).build();
      public static final ConfigBoolean FLUID_SIMPLE_MODE = ConfigBuilders.bool("fluidSimpleMode").defaultValue(false).build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(FLUID_SELECTION_TYPE, FLUID_SIMPLE_MODE, FILL_FLOWING_FLUID, FLUID_REPLACE_BLOCK_LIST, FLUID_LIST);
   }

   public static class Go {
      public static final ConfigBoolean PRINT_SCAN_AUTOWALK = ConfigBuilders.bool("printScanAutoWalk").defaultValue(false).build();
      public static final ConfigBoolean GHAST_PATHFIND = ConfigBuilders.bool("ghastPathfind").defaultValue(false).build();
      public static final ConfigBoolean PATH_NEAREST_TARGET = ConfigBuilders.bool("pathNearestTarget").defaultValue(true).build();
      public static final ConfigBoolean GO_SCAN_EXTRA_BLOCKS = ConfigBuilders.bool("goScanExtraBlocks").defaultValue(false).build();
      public static final ConfigBoolean GO_SCAN_WRONG_BLOCKS = ConfigBuilders.bool("goScanWrongBlocks").defaultValue(false).build();
      public static final ConfigInteger PATH_TARGET_CANDIDATE_LIMIT = ConfigBuilders.integer("pathTargetCandidateLimit")
         .defaultValue(256)
         .range(16, 1024)
         .build();
      public static final ConfigBoolean WALK_SCAN_WHITELIST = ConfigBuilders.bool("walkScanWhitelist").defaultValue(false).build();
      public static final ConfigStringList WALK_SCAN_WHITELIST_LIST = ConfigBuilders.stringList("walkScanWhitelistList").build();
      public static final ConfigOptionList PRINT_SCAN_SECTION_ORDER = ConfigBuilders.optionList("printScanSectionOrder")
         .defaultValue(SectionScanOrderType.XZY)
         .build();
      public static final ConfigBoolean PRINT_SCAN_X_REVERSE = ConfigBuilders.bool("printScanXReverse").defaultValue(false).build();
      public static final ConfigBoolean PRINT_SCAN_Y_REVERSE = ConfigBuilders.bool("printScanYReverse").defaultValue(false).build();
      public static final ConfigBoolean PRINT_SCAN_Z_REVERSE = ConfigBuilders.bool("printScanZReverse").defaultValue(false).build();
      public static final ConfigInteger GO_TIME_LIMIT = ConfigBuilders.integer("goTimeLimit").defaultValue(500).range(100, 2000).build();
      public static final ConfigInteger GO_MAX_FALL = ConfigBuilders.integer("goMaxFall").defaultValue(3).range(1, 10).build();
      public static final ConfigInteger GO_COST_LIMIT_FACTOR = ConfigBuilders.integer("goCostLimitFactor").defaultValue(8).range(0, 32).build();
      public static final ConfigDouble GO_HEURISTIC_WEIGHT = ConfigBuilders.doubleValue("goHeuristicWeight").defaultValue(1.0).range(1.0, 10.0).build();
      public static final ConfigDouble GO_GHAST_COST_ORTHO = ConfigBuilders.doubleValue("goGhastCostOrtho").defaultValue(1.0).range(0.1, 10.0).build();
      public static final ConfigDouble GO_GHAST_COST_DIAG2 = ConfigBuilders.doubleValue("goGhastCostDiag2").defaultValue(1.41421356).range(0.1, 10.0).build();
      public static final ConfigDouble GO_GHAST_COST_DIAG3 = ConfigBuilders.doubleValue("goGhastCostDiag3").defaultValue(1.7320508).range(0.1, 10.0).build();
      public static final ConfigInteger GO_GHAST_ASCEND_MULT = ConfigBuilders.integer("goGhastAscendMult").defaultValue(2).range(1, 32).build();
      public static final ConfigInteger GO_GHAST_DESCEND_MULT = ConfigBuilders.integer("goGhastDescendMult").defaultValue(2).range(1, 32).build();
      public static final ConfigDouble GO_GHAST_WALL_PENALTY = ConfigBuilders.doubleValue("goGhastWallPenalty").defaultValue(6.0).range(0.0, 64.0).build();
      public static final ConfigDouble GO_GHAST_VERT_LATE_WEIGHT = ConfigBuilders.doubleValue("goGhastVertLateWeight")
         .defaultValue(0.2)
         .range(0.0, 2.0)
         .build();
      public static final ConfigDouble GO_GHAST_WAYPOINT_TIMEOUT = ConfigBuilders.doubleValue("goGhastWaypointTimeout")
         .defaultValue(30.0)
         .range(0.0, 600.0)
         .build();
      public static final ConfigDouble GO_GHAST_TURN_PENALTY = ConfigBuilders.doubleValue("goGhastTurnPenalty").defaultValue(0.0).range(0.0, 10.0).build();
      public static final ConfigDouble GO_WALK_COST = ConfigBuilders.doubleValue("goWalkCost").defaultValue(4.633).range(0.1, 60.0).build();
      public static final ConfigDouble GO_WALK_DIAGONAL_COST = ConfigBuilders.doubleValue("goWalkDiagonalCost").defaultValue(6.552).range(0.1, 60.0).build();
      public static final ConfigDouble GO_JUMP_UP_COST = ConfigBuilders.doubleValue("goJumpUpCost").defaultValue(9.633).range(0.1, 60.0).build();
      public static final ConfigDouble GO_WATER_COST = ConfigBuilders.doubleValue("goWaterCost").defaultValue(9.091).range(0.1, 60.0).build();
      public static final ConfigDouble GO_LADDER_COST = ConfigBuilders.doubleValue("goLadderCost").defaultValue(8.511).range(0.1, 60.0).build();
      public static final ConfigDouble GO_LADDER_EXIT_COST = ConfigBuilders.doubleValue("goLadderExitCost").defaultValue(12.511).range(0.1, 60.0).build();
      public static final ConfigDouble GO_PARKOUR_COST = ConfigBuilders.doubleValue("goParkourCost").defaultValue(12.0).range(0.0, 60.0).build();
      public static final ConfigDouble GO_SPRINT_COST = ConfigBuilders.doubleValue("goSprintCost").defaultValue(3.564).range(0.1, 60.0).build();
      public static final ConfigDouble GO_MAX_SPEED = ConfigBuilders.doubleValue("goMaxSpeed").defaultValue(5.7).range(0.5, 10.0).build();
      public static final ConfigBoolean GO_FORCE_SPRINT = ConfigBuilders.bool("goForceSprint").defaultValue(false).build();
      public static final ConfigBoolean GO_TAKEOVER_VIEW = ConfigBuilders.bool("goTakeoverView").defaultValue(false).build();
      public static final ConfigInteger GO_VIEW_OFFSET = ConfigBuilders.integer("goViewOffset").defaultValue(0).range(-180, 180).build();
      public static final ConfigBoolean GO_DEVIATION_STOP = ConfigBuilders.bool("goDeviationStop").defaultValue(true).build();
      public static final ConfigInteger GO_DEVIATION_DISTANCE = ConfigBuilders.integer("goDeviationDistance").defaultValue(1).range(1, 16).build();
      public static final ConfigDouble GO_WAYPOINT_TIMEOUT = ConfigBuilders.doubleValue("goWaypointTimeout").defaultValue(30.0).range(0.0, 600.0).build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
         PRINT_SCAN_AUTOWALK,
         GHAST_PATHFIND,
         PATH_NEAREST_TARGET,
         GO_SCAN_EXTRA_BLOCKS,
         GO_SCAN_WRONG_BLOCKS,
         PATH_TARGET_CANDIDATE_LIMIT,
         WALK_SCAN_WHITELIST,
         WALK_SCAN_WHITELIST_LIST,
         PRINT_SCAN_SECTION_ORDER,
         PRINT_SCAN_X_REVERSE,
         PRINT_SCAN_Y_REVERSE,
         PRINT_SCAN_Z_REVERSE,
         GO_TIME_LIMIT,
         GO_COST_LIMIT_FACTOR,
         GO_HEURISTIC_WEIGHT,
         GO_MAX_FALL,
         GO_GHAST_COST_ORTHO,
         GO_GHAST_COST_DIAG2,
         GO_GHAST_COST_DIAG3,
         GO_GHAST_ASCEND_MULT,
         GO_GHAST_DESCEND_MULT,
         GO_GHAST_WALL_PENALTY,
         GO_GHAST_VERT_LATE_WEIGHT,
         GO_GHAST_WAYPOINT_TIMEOUT,
         GO_GHAST_TURN_PENALTY,
         GO_WALK_COST,
         GO_WALK_DIAGONAL_COST,
         GO_JUMP_UP_COST,
         GO_WATER_COST,
         GO_LADDER_COST,
         GO_LADDER_EXIT_COST,
         GO_PARKOUR_COST,
         GO_SPRINT_COST,
         GO_MAX_SPEED,
         GO_FORCE_SPRINT,
         GO_TAKEOVER_VIEW,
         GO_VIEW_OFFSET,
         GO_DEVIATION_STOP,
         GO_DEVIATION_DISTANCE,
         GO_WAYPOINT_TIMEOUT
      );
   }

   public static class Hotkeys {
      public static final ConfigHotkey OPEN_SCREEN = ConfigBuilders.hotkey("openScreen").defaultStorageString("Z,Y").build();
      public static final ConfigHotkey CLOSE_ALL_MODE = ConfigBuilders.hotkey("closeAllMode").defaultStorageString("LEFT_CONTROL,G").build();
      public static final ConfigHotkey WORK_SWITCH_HOTKEY = ConfigBuilders.hotkey("workingSwitchHotkey")
         .defaultStorageString("CAPS_LOCK")
         .keybindSettings(KeybindSettings.PRESS_ALLOWEXTRA_EMPTY)
         .keybindCallback((action, key) -> Configs.toggleWithNotify(Configs.Core.WORK_SWITCH))
         .build();
      public static final ConfigHotkey PRINT_HOTKEY = ConfigBuilders.hotkey("printHotkey")
         .setVisible(Configs.isMulti)
         .keybindCallback((action, key) -> Configs.toggleWithNotify(Configs.Core.PRINT))
         .build();
      public static final ConfigHotkey MINE_HOTKEY = ConfigBuilders.hotkey("mineHotkey")
         .setVisible(Configs.isMulti)
         .keybindCallback((action, key) -> Configs.toggleWithNotify(Configs.Core.MINE))
         .build();
      public static final ConfigHotkey FILL_HOTKEY = ConfigBuilders.hotkey("fillHotkey")
         .setVisible(Configs.isMulti)
         .keybindCallback((action, key) -> Configs.toggleWithNotify(Configs.Core.FILL))
         .build();
      public static final ConfigHotkey FLUID_HOTKEY = ConfigBuilders.hotkey("fluidHotkey")
         .setVisible(Configs.isMulti)
         .keybindCallback((action, key) -> Configs.toggleWithNotify(Configs.Core.FLUID))
         .build();
      public static final ConfigHotkey PRINT_ICE_FOR_WATER_HOTKEY = ConfigBuilders.hotkey("printIceForWaterHotkey")
         .keybindCallback((action, key) -> Configs.toggleWithNotify(Configs.Print.PRINT_ICE_FOR_WATER))
         .build();
      public static final ConfigHotkey SCAN_AUTOWALK_HOTKEY = ConfigBuilders.hotkey("scanAutoWalkHotkey")
         .keybindCallback((action, key) -> Configs.toggleWithNotify(Configs.Go.PRINT_SCAN_AUTOWALK))
         .build();
      public static final ConfigHotkey REFILL_AMOUNT_ADJUST = ConfigBuilders.hotkey("refillAmountAdjust")
         .defaultStorageString("LEFT_SHIFT,B")
         .setVisible(Configs.isLoadCloudStoreLoaded)
         .build();
      public static final ConfigHotkey SWITCH_PRINTER_MODE = ConfigBuilders.hotkey("switchPrinterMode")
         .bindConfig(Configs.Core.WORK_MODE_TYPE)
         .setVisible(Configs.isSingle)
         .build();
      public static final ConfigBooleanHotkeyed BEDROCK = ConfigBuilders.booleanHotkey("bedrock").defaultValue(false).setVisible(Configs.isMulti).build();
      public static final ConfigHotkey SYNC_INVENTORY = ConfigBuilders.hotkey("syncInventory").build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
         OPEN_SCREEN,
         WORK_SWITCH_HOTKEY,
         CLOSE_ALL_MODE,
         SWITCH_PRINTER_MODE,
         PRINT_HOTKEY,
         MINE_HOTKEY,
         FILL_HOTKEY,
         FLUID_HOTKEY,
         BEDROCK,
         PRINT_ICE_FOR_WATER_HOTKEY,
         SCAN_AUTOWALK_HOTKEY,
         SYNC_INVENTORY,
         new IConfigBase[]{REFILL_AMOUNT_ADJUST}
      );
   }

   public static class Mine {
      public static final ConfigOptionList MINE_SELECTION_TYPE = ConfigBuilders.optionList("mineSelectionType")
         .defaultValue(MineSelectionType.LITEMATICA_SELECTION)
         .build();
       public static final ConfigInteger MINE_PLANE_MIN_Y = ConfigBuilders.integer("minePlaneMinY")
          .defaultValue(-64)
          .range(-512, 512)
          .build();
       public static final ConfigInteger MINE_PLANE_MAX_Y = ConfigBuilders.integer("minePlaneMaxY")
          .defaultValue(320)
          .range(-512, 512)
          .build();
      public static final ConfigOptionList EXCAVATE_LIMITER = ConfigBuilders.optionList("excavateLimiter").defaultValue(ExcavateListMode.CUSTOM).build();
      public static final ConfigOptionList EXCAVATE_LIMIT = ConfigBuilders.optionList("excavateLimit")
         .defaultValue(ListType.NONE)
         .setVisible(Configs.isExcavateCustom)
         .build();
      public static final ConfigStringList EXCAVATE_WHITELIST = ConfigBuilders.stringList("excavateWhitelist").setVisible(Configs.isExcavateWhitelist).build();
      public static final ConfigStringList EXCAVATE_BLACKLIST = ConfigBuilders.stringList("excavateBlacklist").setVisible(Configs.isExcavateBlacklist).build();
      public static final ConfigBoolean BREAK_USE_PACKET = ConfigBuilders.bool("breakUsePacket").defaultValue(false).build();
      public static final ConfigBoolean BREAK_SOUND = ConfigBuilders.bool("breakSound").defaultValue(true).build();
      public static final ConfigInteger BREAK_PROGRESS_THRESHOLD = ConfigBuilders.integer("breakProgressThreshold").defaultValue(100).range(70, 100).build();
      public static final ConfigInteger BREAK_INTERVAL = ConfigBuilders.integer("breakInterval").defaultValue(1).range(0, 20).build();
      public static final ConfigInteger BREAK_BLOCKS_PER_TICK = ConfigBuilders.integer("breakBlocksPerTick").defaultValue(1).range(0, 256).build();
      public static final ConfigInteger BREAK_COOLDOWN = ConfigBuilders.integer("breakCooldown").defaultValue(3).range(0, 64).build();
      public static final ConfigBoolean BREAK_CHECK_HARDNESS = ConfigBuilders.bool("breakCheckHardness").defaultValue(true).build();
      public static final ConfigBoolean BREAK_INSTANT_MINE = ConfigBuilders.bool("breakVeilToggle").defaultValue(false).build();
      public static final ConfigStringList BREAK_INSTANT_MINE_LIST = ConfigBuilders.stringList("breakVeilList").build();
      public static final ConfigBoolean BREAK_USE_DELAYED_DESTROY = ConfigBuilders.bool("breakUseDelayedDestroy").defaultValue(false).build();
      public static final ConfigBoolean BREAK_PARALLEL = ConfigBuilders.bool("breakParallel").defaultValue(false).build();
      public static final ConfigBoolean BREAK_AVOID_FLUID = ConfigBuilders.bool("breakAvoidFluid").defaultValue(false).build();
      public static final ConfigStringList BREAK_FLUID_LIST = ConfigBuilders.stringList("breakFluidList").defaultValue(Blocks.WATER, Blocks.LAVA).build();
      public static final ConfigOptionList BREAK_FLUID_STRATEGY = ConfigBuilders.optionList("breakFluidStrategy")
         .defaultValue(FluidAvoidStrategyType.FIVE_FACES)
         .build();
      public static final ConfigBoolean BREAK_AVOID_SUPPORT = ConfigBuilders.bool("breakAvoidSupport").defaultValue(false).build();
      public static final ConfigBoolean BREAK_NON_BLOCKING = ConfigBuilders.bool("breakNonBlocking").defaultValue(false).build();
      public static final ConfigOptionList BREAK_LIMITER = ConfigBuilders.optionList("breakLimiter").defaultValue(ExcavateListMode.CUSTOM).build();
      public static final ConfigOptionList BREAK_LIMIT = ConfigBuilders.optionList("breakLimit")
         .defaultValue(ListType.NONE)
         .setVisible(Configs.isBreakCustom)
         .build();
      public static final ConfigStringList BREAK_WHITELIST = ConfigBuilders.stringList("breakWhitelist").setVisible(Configs.isBreakWhitelist).build();
      public static final ConfigStringList BREAK_BLACKLIST = ConfigBuilders.stringList("breakBlacklist").setVisible(Configs.isBreakBlacklist).build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
         BREAK_USE_PACKET,
         BREAK_SOUND,
         MINE_SELECTION_TYPE,
         MINE_PLANE_MIN_Y,
         MINE_PLANE_MAX_Y,
         Configs.Core.ITERATOR_SHAPE,
         EXCAVATE_LIMITER,
         EXCAVATE_LIMIT,
         EXCAVATE_WHITELIST,
         EXCAVATE_BLACKLIST,
         BREAK_CHECK_HARDNESS,
         BREAK_INSTANT_MINE,
         BREAK_INSTANT_MINE_LIST,
         BREAK_AVOID_FLUID,
         BREAK_FLUID_LIST,
         BREAK_FLUID_STRATEGY,
         BREAK_AVOID_SUPPORT,
         BREAK_NON_BLOCKING,
         BREAK_PARALLEL,
         BREAK_USE_DELAYED_DESTROY,
         BREAK_INTERVAL,
         BREAK_BLOCKS_PER_TICK,
         BREAK_COOLDOWN,
         BREAK_PROGRESS_THRESHOLD,
         BREAK_LIMITER,
         BREAK_LIMIT,
         BREAK_WHITELIST,
         BREAK_BLACKLIST
      );
   }

   public static class Bedrock {
      public static final ConfigStringList BEDROCK_LIST = ConfigBuilders.stringList("bedrockList")
         .defaultValue("minecraft:bedrock")
         .build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.<IConfigBase>of(
         Configs.Hotkeys.BEDROCK,
         BEDROCK_LIST,
         Configs.Mine.BREAK_INTERVAL,
         Configs.Mine.BREAK_BLOCKS_PER_TICK
      );
   }

   public static class Print {
      public static final ConfigOptionList PRINT_SELECTION_TYPE = ConfigBuilders.optionList("printSelectionType")
         .defaultValue(SelectionType.LITEMATICA_RENDER_LAYER)
         .build();
      public static final ConfigBoolean EASY_PLACE_PROTOCOL = ConfigBuilders.bool("easyPlaceProtocol").defaultValue(false).build();
      public static final ConfigBoolean PLACE_IN_AIR = ConfigBuilders.bool("placeInAir").defaultValue(true).build();
      public static final ConfigBoolean PRINT_FAST_DIRECTIONAL_PLACEMENT = ConfigBuilders.bool("printFastDirectionalPlacement").defaultValue(false).build();
      public static final ConfigBoolean PRINT_ONLY_EMPTY_SHULKER = ConfigBuilders.bool("printOnlyEmptyShulker").defaultValue(false).build();
      public static final ConfigBoolean PRINT_SKIP_SHULKER = ConfigBuilders.bool("printSkipShulker").defaultValue(false).build();
      public static final ConfigBoolean PRINT_SHULKER_AFTER_ORDINARY = ConfigBuilders.bool("printShulkerAfterOrdinary").defaultValue(true).build();
      public static final ConfigOptionList PLACE_DEFAULT_DIRECTION = ConfigBuilders.optionList("placeDefaultDirection")
         .defaultValue(DefaultPlaceDirectionType.NONE)
         .build();
      public static final ConfigBoolean REPAIR_RAIL_SHAPE = ConfigBuilders.bool("printRepairRailShape").defaultValue(false).build();
      public static final ConfigBoolean PRINT_SKIP = ConfigBuilders.bool("printSkip").defaultValue(false).build();
      public static final ConfigStringList PRINT_SKIP_LIST = ConfigBuilders.stringList("printSkipList").build();
      public static final ConfigBoolean PRINT_SCAN_WHITELIST = ConfigBuilders.bool("printScanWhitelist").defaultValue(false).build();
      public static final ConfigStringList PRINT_SCAN_WHITELIST_LIST = ConfigBuilders.stringList("printScanWhitelistList").build();
      public static final ConfigBoolean PRINT_FORCED_SNEAK = ConfigBuilders.bool("printForcedSneak").defaultValue(false).build();
      public static final ConfigBoolean PRINT_RESERVE_ITEMS = ConfigBuilders.bool("printReserveItems").defaultValue(false).build();
      public static final ConfigInteger PRINT_RESERVE_ITEM_COUNT = ConfigBuilders.integer("printReserveItemCount").defaultValue(1).range(1, 64).build();
      public static final ConfigBoolean PRINT_REPLACE = ConfigBuilders.bool("printReplace").defaultValue(true).build();
      public static final ConfigStringList REPLACEABLE_LIST = ConfigBuilders.stringList("printReplaceableList")
         .defaultValue(Blocks.SNOW, Blocks.LAVA, Blocks.WATER, Blocks.BUBBLE_COLUMN, Blocks.SHORT_GRASS)
         .build();
      public static final ConfigBoolean SUBSTITUTE_PLACEMENT = ConfigBuilders.bool("printSubstitutePlacement").defaultValue(false).build();
      public static final ConfigStringList SUBSTITUTE_LIST = ConfigBuilders.stringList("printSubstituteList")
         .defaultValue(
            "tube_coral:dead_tube_coral",
            "tube_coral_block:dead_tube_coral_block",
            "tube_coral_fan:dead_tube_coral_fan",
            "tube_coral_wall_fan:dead_tube_coral_wall_fan",
            "brain_coral:dead_brain_coral",
            "brain_coral_block:dead_brain_coral_block",
            "brain_coral_fan:dead_brain_coral_fan",
            "brain_coral_wall_fan:dead_brain_coral_wall_fan",
            "bubble_coral:dead_bubble_coral",
            "bubble_coral_block:dead_bubble_coral_block",
            "bubble_coral_fan:dead_bubble_coral_fan",
            "bubble_coral_wall_fan:dead_bubble_coral_wall_fan",
            "fire_coral:dead_fire_coral",
            "fire_coral_block:dead_fire_coral_block",
            "fire_coral_fan:dead_fire_coral_fan",
            "fire_coral_wall_fan:dead_fire_coral_wall_fan",
            "horn_coral:dead_horn_coral",
            "horn_coral_block:dead_horn_coral_block",
            "horn_coral_fan:dead_horn_coral_fan",
            "horn_coral_wall_fan:dead_horn_coral_wall_fan"
         )
         .build();
      public static final ConfigBoolean PRINT_ICE_FOR_WATER = ConfigBuilders.bool("printIceForWater").defaultValue(false).build();
      public static final ConfigBoolean PRINT_ICE_FOR_WATER_OPTIMIZED = ConfigBuilders.bool("printIceForWaterOptimized").defaultValue(false).build();
      public static final ConfigBoolean FILL_CAULDRON = ConfigBuilders.bool("printFillCauldron").defaultValue(false).build();
      public static final ConfigBoolean STRIP_LOGS = ConfigBuilders.bool("printAutoStripLogs").defaultValue(false).build();
      public static final ConfigBoolean NOTE_BLOCK_TUNING = ConfigBuilders.bool("printAutoTuning").defaultValue(true).build();
      public static final ConfigBoolean SAFELY_OBSERVER = ConfigBuilders.bool("printSafelyObserver").defaultValue(true).build();
      public static final ConfigBoolean FILL_COMPOSTER = ConfigBuilders.bool("printAutoFillComposter").defaultValue(false).build();
      public static final ConfigStringList FILL_COMPOSTER_WHITELIST = ConfigBuilders.stringList("printAutoFillComposterWhitelist")
         .setVisible(FILL_COMPOSTER::getBooleanValue)
         .build();
      public static final ConfigBoolean BONEMEAL_CROPS = ConfigBuilders.bool("printBonemealCrops").defaultValue(false).build();
      public static final ConfigInteger BONEMEAL_CROPS_CLICKS = ConfigBuilders.integer("printBonemealCropsClicks")
         .defaultValue(10)
         .range(1, 32)
         .setVisible(BONEMEAL_CROPS::getBooleanValue)
         .build();
      public static final ConfigBoolean BREAK_WRONG_BLOCK = ConfigBuilders.bool("printBreakWrongBlock").defaultValue(false).build();
      public static final ConfigBoolean BREAK_EXTRA_BLOCK = ConfigBuilders.bool("printBreakExtraBlock").defaultValue(false).build();
      public static final ConfigBoolean BREAK_WRONG_STATE_BLOCK = ConfigBuilders.bool("printBreakWrongStateBlock").defaultValue(false).build();
      public static final ConfigBoolean PRINT_USE_PACKET = ConfigBuilders.bool("placeUsePacket").defaultValue(false).build();
      public static final ConfigBoolean PRINT_SOUND = ConfigBuilders.bool("printSound").defaultValue(true).build();
      public static final ConfigInteger PLACE_INTERVAL = ConfigBuilders.integer("placeInterval").defaultValue(1).range(0, 20).build();
      public static final ConfigInteger PLACE_BLOCKS_PER_TICK = ConfigBuilders.integer("placeBlocksPerTick").defaultValue(1).range(0, 256).build();
      public static final ConfigBoolean PLACE_SAME_ITEM_FIRST = ConfigBuilders.bool("placeSameItemFirst").defaultValue(false).build();
      public static final ConfigBoolean SPHERICAL_PLACE = ConfigBuilders.bool("sphericalPlace").defaultValue(false).build();
      public static final ConfigInteger ITEM_SWITCH_INTERVAL = ConfigBuilders.integer("itemSwitchInterval").defaultValue(0).range(0, 200).build();
      public static final ConfigInteger PLACE_COOLDOWN = ConfigBuilders.integer("placeCooldown").defaultValue(3).range(1, 64).build();
      public static final ConfigBoolean FALLING_CHECK = ConfigBuilders.bool("printFallingBlockCheck").defaultValue(true).build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
         PRINT_USE_PACKET,
         PRINT_SOUND,
         PRINT_SELECTION_TYPE,
         EASY_PLACE_PROTOCOL,
         PLACE_IN_AIR,
         PRINT_FORCED_SNEAK,
         BREAK_WRONG_BLOCK,
         BREAK_EXTRA_BLOCK,
         BREAK_WRONG_STATE_BLOCK,
         PRINT_SKIP,
         PRINT_SKIP_LIST,
         PRINT_SCAN_WHITELIST,
         PRINT_SCAN_WHITELIST_LIST,
         PRINT_REPLACE,
         REPLACEABLE_LIST,
         PRINT_ICE_FOR_WATER,
         PRINT_ICE_FOR_WATER_OPTIMIZED,
         FILL_CAULDRON,
         SAFELY_OBSERVER,
         STRIP_LOGS,
         NOTE_BLOCK_TUNING,
         SUBSTITUTE_PLACEMENT,
         SUBSTITUTE_LIST,
         FILL_COMPOSTER,
         FILL_COMPOSTER_WHITELIST,
         BONEMEAL_CROPS,
         BONEMEAL_CROPS_CLICKS,
         PRINT_FAST_DIRECTIONAL_PLACEMENT,
         PRINT_ONLY_EMPTY_SHULKER,
         PRINT_SHULKER_AFTER_ORDINARY,
         PRINT_SKIP_SHULKER,
         PLACE_DEFAULT_DIRECTION,
         REPAIR_RAIL_SHAPE,
         PRINT_RESERVE_ITEMS,
         PRINT_RESERVE_ITEM_COUNT,
         PLACE_INTERVAL,
         PLACE_BLOCKS_PER_TICK,
         PLACE_SAME_ITEM_FIRST,
         SPHERICAL_PLACE,
         ITEM_SWITCH_INTERVAL,
         PLACE_COOLDOWN,
         FALLING_CHECK
      );
   }

   public static class Special {
      public static final ConfigBoolean UNLOCK_BEACON_EFFECTS = ConfigBuilders.bool("unlockBeaconEffects").defaultValue(false).build();
      public static final ConfigBoolean TWEAKEROO_ANGEL_BLOCK_MAY_BUILD = ConfigBuilders.bool("tweakerooAngelBlockMayBuild")
         .defaultValue(false)
         .setVisible(ModUtils::isTweakerooLoaded)
         .build();
      public static final ConfigBoolean RENDER_ONLY_BLOCKS = ConfigBuilders.bool("renderOnlyBlocks").defaultValue(false).build();
      public static final ConfigStringList RENDER_ONLY_BLOCK_LIST = ConfigBuilders.stringList("renderOnlyBlockList").build();
      public static final ConfigBoolean PRINT_CLOUD_STORE_REFILL = ConfigBuilders.bool("printCloudStoreRefill")
         .defaultValue(false)
         .setVisible(Configs.isLoadCloudStoreLoaded)
         .build();
      public static final ConfigBoolean PRINT_CLOUD_STORE_MANUAL_REFILL = ConfigBuilders.bool("printCloudStoreManualRefill")
         .defaultValue(false)
         .setVisible(Configs.isLoadCloudStoreLoaded)
         .build();
      public static final ConfigInteger PRINT_CLOUD_STORE_REFILL_COOLDOWN = ConfigBuilders.integer("printCloudStoreRefillCooldown")
         .defaultValue(300)
         .range(10, 3600)
         .setVisible(Configs.isLoadCloudStoreLoaded)
         .build();
      public static final ConfigInteger PRINT_CLOUD_STORE_REFILL_AMOUNT = ConfigBuilders.integer("printCloudStoreRefillAmount")
         .defaultValue(64)
         .range(1, 64)
         .setVisible(Configs.isLoadCloudStoreLoaded)
         .build();
      public static final ConfigBoolean REFILL_SCROLL_REVERSE = ConfigBuilders.bool("refillScrollReverse")
         .defaultValue(false)
         .setVisible(Configs.isLoadCloudStoreLoaded)
         .build();
      public static final ConfigBoolean PRINT_CLOUD_STORE_MIDDLE_CLICK_FORCE = ConfigBuilders.bool("printCloudStoreMiddleClickForce")
         .defaultValue(false)
         .setVisible(Configs.isLoadCloudStoreLoaded)
         .build();
      public static final ConfigOptionList EAT = ConfigBuilders.optionList("eat").defaultValue(EatMode.OFF).build();
      public static final ConfigInteger EAT_HUNGER_THRESHOLD = ConfigBuilders.integer("eatHungerThreshold").defaultValue(14).range(1, 19).build();
      public static final ConfigStringList EAT_BLACKLIST = ConfigBuilders.stringList("eatBlacklist")
         .defaultValue(
            "minecraft:rotten_flesh",
            "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple",
            "minecraft:beef",
            "minecraft:porkchop",
            "minecraft:chicken",
            "minecraft:mutton",
            "minecraft:rabbit",
            "minecraft:cod",
            "minecraft:salmon",
            "minecraft:tropical_fish",
            "minecraft:potato",
            "minecraft:pufferfish",
            "minecraft:suspicious_stew",
            "minecraft:chorus_fruit",
            "minecraft:poisonous_potato",
            "minecraft:spider_eye"
         )
         .build();
      public static final ConfigInteger EAT_HURT_CANCEL_COOLDOWN = ConfigBuilders.integer("eatHurtCancelCooldown").defaultValue(7).range(0, 60).build();
      public static final ConfigBoolean SYNC_INVENTORY_CHECK = ConfigBuilders.bool("syncInventoryCheck").defaultValue(false).build();
      public static final ConfigBoolean SYNC_INVENTORY_CRAFTER = ConfigBuilders.bool("syncInventoryCrafter").defaultValue(true).build();
      public static final ConfigInteger SYNC_PACKET_LIMIT = ConfigBuilders.integer("syncPacketLimit").defaultValue(16).range(1, 1024).build();
      public static final ConfigColor SYNC_INVENTORY_COLOR = ConfigBuilders.color("syncInventoryColor").defaultValue("#4CFF4CE6").build();
      public static final ConfigInteger SYNC_HIGHLIGHT_RENDER_DISTANCE = ConfigBuilders.integer("syncHighlightRenderDistance")
         .defaultValue(64)
         .range(0, 256)
         .build();
      public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
         UNLOCK_BEACON_EFFECTS,
         TWEAKEROO_ANGEL_BLOCK_MAY_BUILD,
         RENDER_ONLY_BLOCKS,
         RENDER_ONLY_BLOCK_LIST,
         PRINT_CLOUD_STORE_REFILL,
         PRINT_CLOUD_STORE_MANUAL_REFILL,
         PRINT_CLOUD_STORE_MIDDLE_CLICK_FORCE,
         PRINT_CLOUD_STORE_REFILL_COOLDOWN,
         PRINT_CLOUD_STORE_REFILL_AMOUNT,
         REFILL_SCROLL_REVERSE,
         SYNC_INVENTORY_CHECK,
         SYNC_INVENTORY_CRAFTER,
         SYNC_PACKET_LIMIT, SYNC_INVENTORY_COLOR, SYNC_HIGHLIGHT_RENDER_DISTANCE, EAT, EAT_HUNGER_THRESHOLD, EAT_BLACKLIST, EAT_HURT_CANCEL_COOLDOWN
      );
   }
}
