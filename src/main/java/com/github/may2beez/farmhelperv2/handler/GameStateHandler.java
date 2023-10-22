package com.github.may2beez.farmhelperv2.handler;

import com.github.may2beez.farmhelperv2.config.FarmHelperConfig;
import com.github.may2beez.farmhelperv2.feature.impl.Failsafe;
import com.github.may2beez.farmhelperv2.mixin.gui.IGuiPlayerTabOverlayAccessor;
import com.github.may2beez.farmhelperv2.util.*;
import com.github.may2beez.farmhelperv2.util.helper.Clock;
import com.github.may2beez.farmhelperv2.util.helper.Timer;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameStateHandler {
    private final Minecraft mc = Minecraft.getMinecraft();
    private static GameStateHandler INSTANCE;
    public static GameStateHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GameStateHandler();
        }
        return INSTANCE;
    }

    @Getter
    public enum Location {
        PRIVATE_ISLAND("Private Island"),
        HUB("Hub"),
        THE_PARK("The Park"),
        THE_FARMING_ISLANDS("The Farming Islands"),
        SPIDER_DEN("Spider's Den"),
        THE_END("The End"),
        CRIMSON_ISLE("Crimson Isle"),
        GOLD_MINE("Gold Mine"),
        DEEP_CAVERNS("Deep Caverns"),
        DWARVEN_MINES("Dwarven Mines"),
        CRYSTAL_HOLLOWS("Crystal Hollows"),
        JERRY_WORKSHOP("Jerry's Workshop"),
        DUNGEON_HUB("Dungeon Hub"),
        LIMBO("UNKNOWN"),
        LOBBY("PROTOTYPE"),
        GARDEN("Garden"),
        DUNGEON("Dungeon"),
        TELEPORTING("Teleporting");

        private final String name;

        Location(String name) {
            this.name = name;
        }
    }

    @Getter
    private Location location = Location.TELEPORTING;
    private final Pattern areaPattern = Pattern.compile("Area:\\s(.+)");

    @Getter
    private boolean frontWalkable;
    @Getter
    private boolean rightWalkable;
    @Getter
    private boolean backWalkable;
    @Getter
    private boolean leftWalkable;

    @Getter
    private double dx;
    @Getter
    private double dz;
    @Getter
    private double dy;

    @Getter
    private String serverIP;
    private final Timer notMovingTimer = new Timer();
    private long randomValueToWait = FarmHelperConfig.getRandomTimeBetweenChangingRows();

    public enum BuffState {
        ACTIVE,
        NOT_ACTIVE,
        UNKNOWN
    }

    @Getter
    private BuffState cookieBuffState = BuffState.UNKNOWN;

    @Getter
    private BuffState godPotState = BuffState.UNKNOWN;

    @Getter
    private double currentPurse = 0;

    @Getter
    private double previousPurse = 0;

    @Getter
    private long bits = 0;

    @Getter
    private long copper = 0;

    @Getter
    private Optional<FarmHelperConfig.CropEnum> jacobsContestCrop = Optional.empty();

    @Getter
    private int jacobsContestCropNumber = 0;

    private enum JacobMedal {
        NONE,
        BRONZE,
        SILVER,
        GOLD
    }

    @Getter
    private JacobMedal jacobMedal = JacobMedal.NONE;

    @Getter
    private final Clock jacobContestLeftClock = new Clock();

    private final Pattern jacobsRemainingTimePattern = Pattern.compile("([0-9]|[1-2][0-9])m([0-9]|[1-5][0-9])s");

    @SubscribeEvent
    public void onWorldChange(WorldEvent.Unload event) {
        location = Location.TELEPORTING;
    }

    @SubscribeEvent
    public void onTickCheckCoins(TickEvent.PlayerTickEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        List<String> scoreboardLines = ScoreboardUtils.getScoreboardLines();
        if (scoreboardLines.isEmpty()) return;

        for (String line : scoreboardLines) {
            String cleanedLine = StringUtils.stripControlCodes(ScoreboardUtils.cleanSB(line));
            if (cleanedLine.contains("Purse:") || cleanedLine.contains("Piggy:")) {
                try {
                    String stringPurse = cleanedLine.split(" ")[1].replace(",", "").trim();
                    if (stringPurse.contains("(+")) {
                        stringPurse = stringPurse.substring(0, stringPurse.indexOf("("));
                    }
                    long tempCurrentPurse = Long.parseLong(stringPurse);
                    previousPurse = currentPurse;
                    currentPurse = tempCurrentPurse;
                } catch (NumberFormatException ignored) {}
            } else if (cleanedLine.contains("Bits:")) {
                try {
                    String stringBits = cleanedLine.split(" ")[1].replace(",", "").trim();
                    if (stringBits.contains("(+")) {
                        stringBits = stringBits.substring(0, stringBits.indexOf("("));
                    }
                    bits = Long.parseLong(stringBits);
                } catch (NumberFormatException ignored) {}
            } else if (cleanedLine.contains("Copper:")) {
                try {
                    String stringCopper = cleanedLine.split(" ")[1].replace(",", "").trim();
                    if (stringCopper.contains("(+")) {
                        stringCopper = stringCopper.substring(0, stringCopper.indexOf("("));
                    }
                    copper = Long.parseLong(stringCopper);
                } catch (NumberFormatException ignored) {}
            }
            if (inJacobContest()) {
                try {
                    if (cleanedLine.contains("with")) {
                        jacobsContestCropNumber = Integer.parseInt(cleanedLine.substring(cleanedLine.lastIndexOf(" ") + 1).replace(",", ""));
                    }
                } catch (NumberFormatException ignored) {
                    jacobsContestCropNumber = 0;
                }
                if (!jacobContestLeftClock.isScheduled()) {
                    Matcher matcher = jacobsRemainingTimePattern.matcher(cleanedLine);
                    if (matcher.find()) {
                        if (cleanedLine.contains("Wheat")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.WHEAT);
                        } else if (cleanedLine.contains("Carrot")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.CARROT);
                        } else if (cleanedLine.contains("Potato")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.POTATO);
                        } else if (cleanedLine.contains("Nether") || cleanedLine.contains("Wart")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.NETHER_WART);
                        } else if (cleanedLine.contains("Sugar") || cleanedLine.contains("Cane")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.SUGAR_CANE);
                        } else if (cleanedLine.contains("Mushroom")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.MUSHROOM);
                        } else if (cleanedLine.contains("Melon")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.MELON);
                        } else if (cleanedLine.contains("Pumpkin")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.PUMPKIN);
                        } else if (cleanedLine.contains("Cocoa") || cleanedLine.contains("Bean")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.COCOA_BEANS);
                        } else if (cleanedLine.contains("Cactus")) {
                            jacobsContestCrop = Optional.of(FarmHelperConfig.CropEnum.CACTUS);
                        }

                        String minutes = matcher.group(1);
                        String seconds = matcher.group(2);
                        jacobContestLeftClock.schedule((Long.parseLong(minutes) * 60 + Long.parseLong(seconds)) * 1_000L);
                    }
                }
                if (cleanedLine.contains("BRONZE with")) {
                    jacobMedal = JacobMedal.BRONZE;
                } else if (cleanedLine.contains("SILVER with")) {
                    jacobMedal = JacobMedal.SILVER;
                } else if (cleanedLine.contains("GOLD with")) {
                    jacobMedal = JacobMedal.GOLD;
                }
            } else {
                jacobsContestCrop = Optional.empty();
                jacobsContestCropNumber = 0;
                jacobMedal = JacobMedal.NONE;
            }
        }
    }

    @SubscribeEvent
    public void onTickCheckBuffs(TickEvent.ClientTickEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        boolean foundGodPotBuff = false;
        boolean foundCookieBuff = false;
        boolean loaded = false;

        IGuiPlayerTabOverlayAccessor tabOverlay = (IGuiPlayerTabOverlayAccessor) mc.ingameGUI.getTabList();
        if (tabOverlay == null) return;
        IChatComponent footer = tabOverlay.getFooter();
        if (footer == null || footer.getFormattedText().isEmpty()) return;
        String[] footerString = footer.getFormattedText().split("\n");

        for (String line : footerString) {
            String unformattedLine = StringUtils.stripControlCodes(line);
            if (unformattedLine.contains("Active Effects")) {
                loaded = true;
            }
            if (unformattedLine.contains("You have a God Potion active!")) {
                foundGodPotBuff = true;
                continue;
            }
            if (unformattedLine.contains("Cookie Buff")) {
                foundCookieBuff = true;
                continue;
            }
            if (foundCookieBuff) {
                if (unformattedLine.contains("Not active")) {
                    foundCookieBuff = false;
                }
                break;
            }
        }

        if (!loaded) {
            cookieBuffState = BuffState.UNKNOWN;
            godPotState = BuffState.UNKNOWN;
            return;
        }

        cookieBuffState = foundCookieBuff ? BuffState.ACTIVE : BuffState.NOT_ACTIVE;
        godPotState = foundGodPotBuff ? BuffState.ACTIVE : BuffState.NOT_ACTIVE;
    }

    @SubscribeEvent
    public void onTickCheckLocation(TickEvent.ClientTickEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        if (mc.getCurrentServerData() != null && mc.getCurrentServerData().serverIP != null) {
            serverIP = mc.getCurrentServerData().serverIP;
        }

        if (TablistUtils.getTabList().size() == 1 && ScoreboardUtils.getScoreboardLines().isEmpty() && PlayerUtils.isInventoryEmpty(mc.thePlayer)) {
            location = Location.LIMBO;
            return;
        }

        for (String line : TablistUtils.getTabList()) {
            Matcher matcher = areaPattern.matcher(line);
            if (matcher.find()) {
                String area = matcher.group(1);
                for (Location island : Location.values()) {
                    if (area.equals(island.getName())) {
                        location = island;
                        return;
                    }
                }
            }
        }
        if (ScoreboardUtils.getScoreboardTitle().contains("PROTOTYPE") || ScoreboardUtils.getScoreboardTitle().contains("HYPIXEL")) {
            location = Location.LOBBY;
            return;
        }
        location = Location.TELEPORTING;
    }

    @SubscribeEvent
    public void onTickCheckMoving(TickEvent.ClientTickEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        dx = Math.abs(mc.thePlayer.posX - mc.thePlayer.lastTickPosX);
        dz = Math.abs(mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ);
        dy = Math.abs(mc.thePlayer.posY - mc.thePlayer.lastTickPosY);

        if (notMoving()) {
            if (hasPassedSinceStopped()) {
                if (Failsafe.getInstance().hasDirtBlocks() && Failsafe.getInstance().isTouchingDirtBlock()) {
                    Failsafe.getInstance().addEmergency(Failsafe.EmergencyType.DIRT_CHECK);
                    return;
                }
                randomValueToWaitNextTime = -1;
                notMovingTimer.reset();
            }
        } else {
            if (!notMovingTimer.isScheduled())
                randomValueToWait = FarmHelperConfig.getRandomTimeBetweenChangingRows();
            notMovingTimer.schedule();
        }
        float yaw;
        if (MacroHandler.getInstance().getCurrentMacro().isPresent() && MacroHandler.getInstance().getCurrentMacro().get().getClosest90Deg() != -1337) {
            yaw = MacroHandler.getInstance().getCurrentMacro().get().getClosest90Deg();
        } else {
            yaw = mc.thePlayer.rotationYaw;
        }
        frontWalkable = BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(0, 0, 1, yaw));
        rightWalkable = (BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(1, 0, 0, yaw)));
        backWalkable = (BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(0, 0, -1, yaw)));
        leftWalkable = (BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(-1, 0, 0, yaw)));
    }

    public boolean hasPassedSinceStopped() {
        return notMovingTimer.hasPassed(randomValueToWaitNextTime != -1 ? randomValueToWaitNextTime : randomValueToWait);
    }

    public boolean notMoving() {
        return dx < 0.01 && dz < 0.01 && dy < 0.01 && mc.currentScreen == null;
    }

    public boolean canChangeDirection() {
        return !notMovingTimer.isScheduled();
    }

    private long randomValueToWaitNextTime = -1;

    public void scheduleNotMoving(int time) {
        randomValueToWaitNextTime = time;
        notMovingTimer.schedule();
    }
    public void scheduleNotMoving() {
        randomValueToWait = FarmHelperConfig.getRandomTimeBetweenChangingRows();
        notMovingTimer.schedule();
    }
    public boolean inGarden() {
        return location != Location.TELEPORTING && location == Location.GARDEN;
    }

    @Getter
    @Setter
    private boolean wasInJacobContest = false;

    public boolean inJacobContest() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String cleanedLine = ScoreboardUtils.cleanSB(line);
            if ((cleanedLine.toLowerCase()).contains("jacob's contest")) {
                return true;
            }
        }
        return false;
    }
}
