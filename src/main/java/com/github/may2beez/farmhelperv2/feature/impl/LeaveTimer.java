package com.github.may2beez.farmhelperv2.feature.impl;

import cc.polyfrost.oneconfig.utils.Multithreading;
import com.github.may2beez.farmhelperv2.config.FarmHelperConfig;
import com.github.may2beez.farmhelperv2.feature.FeatureManager;
import com.github.may2beez.farmhelperv2.feature.IFeature;
import com.github.may2beez.farmhelperv2.handler.GameStateHandler;
import com.github.may2beez.farmhelperv2.handler.MacroHandler;
import com.github.may2beez.farmhelperv2.util.LogUtils;
import com.github.may2beez.farmhelperv2.util.helper.AudioManager;
import com.github.may2beez.farmhelperv2.util.helper.Clock;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.crypto.Mac;
import java.util.concurrent.TimeUnit;

public class LeaveTimer implements IFeature {
    private final Minecraft mc = Minecraft.getMinecraft();
    private static LeaveTimer instance;

    public static LeaveTimer getInstance() {
        if (instance == null) {
            instance = new LeaveTimer();
        }
        return instance;
    }

    public static final Clock leaveClock = new Clock();

    @Override
    public String getName() {
        return "Leave Timer";
    }

    @Override
    public boolean isRunning() {
        return leaveClock.isScheduled();
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return false;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return isToggled();
    }

    @Override
    public void start() {
        LogUtils.sendDebug("Starting leave timer");
        leaveClock.schedule(FarmHelperConfig.leaveTime * 60 * 1000L);
    }

    @Override
    public void stop() {
        leaveClock.reset();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        leaveClock.reset();
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.leaveTimer;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!isRunning()) return;
        if (Failsafe.getInstance().isEmergency()) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;
        if (leaveClock.isScheduled() && leaveClock.passed()) {
            LogUtils.sendDebug("Leave timer has ended");
            leaveClock.reset();
            MacroHandler.getInstance().disableMacro();
            Multithreading.schedule(() -> {
                try {
                    mc.getNetHandler().getNetworkManager().closeChannel(new ChatComponentText("The timer has ended"));
                    AudioManager.getInstance().resetSound();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 500, TimeUnit.MILLISECONDS);
        }
    }
}