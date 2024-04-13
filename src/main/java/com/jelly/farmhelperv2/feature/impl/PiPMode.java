package com.jelly.farmhelperv2.feature.impl;

import cc.polyfrost.oneconfig.utils.Multithreading;
import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.feature.IFeature;
import com.jelly.farmhelperv2.util.LogUtils;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.SystemUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.awt.*;
import java.util.concurrent.TimeUnit;

// This class is responsible for the Picture-in-Picture mode feature.
// This feature is only available on Windows.
// This feature is used to make the game window smaller and always on top.
// Made by CatalizCS with love <3
// spent 6 hours coding and debugging this feature :pray:

public class PiPMode implements IFeature {
    private boolean enabled = false;
    private DisplayMode displayMode;
    private int x;
    private int y;
    private int width;
    private int height;
    private final Minecraft mc = Minecraft.getMinecraft();

    private static PiPMode instance;

    public static PiPMode getInstance() {
        if (instance == null) {
            instance = new PiPMode();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "PiPMode";
    }

    @Override
    public boolean isRunning() {
        return enabled;
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
        if (!SystemUtils.IS_OS_WINDOWS) {
            LogUtils.sendError("[PiPMode] This feature is only available on Windows.");
            FarmHelperConfig.pipMode = false;
            return;
        }

        DisplayMode displayMode = Display.getDisplayMode();
        width = displayMode.getWidth();
        height = displayMode.getHeight();

        LogUtils.sendDebug("[PiPMode] Enabled.");
        setPiPMode(true);
        enabled = true;
    }

    @Override
    public void stop() {
        LogUtils.sendDebug("[PiPMode] Disabled.");
        setPiPMode(false);
        enabled = false;
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        if (!isToggled() || !isRunning()) return;
        setPiPMode(false);
    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.pipMode;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    public void setAlwaysOnTop(boolean enabled) {
        try {
            User32 user32 = User32.INSTANCE;
            char[] buffer = new char[1024];

            user32.EnumWindows((hWnd, data) -> {
                user32.GetWindowText(hWnd, buffer, buffer.length);
                String title = Native.toString(buffer);
                if (title.contains("Farm Helper") && title.contains(mc.getSession().getUsername())) {
                    user32.SetWindowPos(hWnd, new WinDef.HWND(Pointer.createConstant(enabled ? -1 : -2)), 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0008);
                    return false;
                }
                return true;
            }, null);
        } catch (Exception e) {
            LogUtils.sendError("[PiPMode] Failed to set or unset always on top: " + e.getMessage());
        }

    }

    public void setPiPMode(boolean enabled) {
        try {
            if (enabled) {
                width = Display.getWidth();
                height = Display.getHeight();
                x = Display.getX();
                y = Display.getY();
                displayMode = Display.getDisplayMode();
                LogUtils.sendDebug("Width: " + width + " Height: " + height + " X: " + x + " Y: " + y);
                System.setProperty("org.lwjgl.opengl.Window.undecorated", "true");
                Display.setDisplayMode(new DisplayMode(420, 252));
                Display.setFullscreen(false);
                Display.setResizable(false);

                mc.resize(Display.getWidth(), Display.getHeight());

                Display.setLocation(-1, -1);
                Robot robot = new Robot();
                robot.mouseMove(Display.getX() + Display.getWidth() / 2, Display.getY() + Display.getHeight() / 2);

                setAlwaysOnTop(true);
            } else {
                System.setProperty("org.lwjgl.opengl.Window.undecorated", "false");
                Display.setDisplayMode(this.displayMode);
                Display.setResizable(true);

                Display.setResizable(false);
                Display.setResizable(true);

                mc.resize(Display.getWidth(), Display.getHeight());

                Display.setLocation(-1, -1);
                setAlwaysOnTop(false);
                User32 user32 = User32.INSTANCE;
                float prevMouseSpeed = mc.gameSettings.mouseSensitivity;
                mc.gameSettings.mouseSensitivity = 0;
                user32.PostMessage(User32.INSTANCE.GetForegroundWindow(), 0x0112, new WinDef.WPARAM(0xF030), new WinDef.LPARAM(0));
                Multithreading.schedule(() -> mc.gameSettings.mouseSensitivity = prevMouseSpeed, 200, TimeUnit.MILLISECONDS);

                width = 0;
                height = 0;

                previousX = 0;
                previousY = 0;

                x = 0;
                y = 0;

                displayMode = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    int previousX = 0;
    int previousY = 0;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!FarmHelperConfig.pipMode || event.phase == TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null || !isRunning())
            return;

//        Need optimize this code later
        if (Mouse.isButtonDown(2)) {

            Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
            int mouseX = (int) mouseLocation.getX();
            int mouseY = (int) mouseLocation.getY();

            if (previousX == 0 && previousY == 0) {
                previousX = mouseX;
                previousY = mouseY;
            }

            int dx = mouseX - previousX;
            int dy = mouseY - previousY;

            int newX = Display.getX() + dx;
            int newY = Display.getY() + dy;

            previousX = mouseX;
            previousY = mouseY;

            Display.setLocation(newX, newY);
        }
    }
}