package com.badlogic.gdx.backends.lwjgl3;

import org.lwjgl.glfw.GLFW;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class OnionExtension {
    //This is the latest version of the setWindowedMode method, which isn't in the latest available version of libgdx.
    //If an update arrives, this SHOULD be removed.
    public static void setBorderlessFullscreen(Lwjgl3Graphics graphics, int width, int height) {
        Lwjgl3Window window = graphics.getWindow();

        window.getInput().resetPollingStates();
        if (!graphics.isFullscreen()) {
            int newX = 0, newY = 0;
            boolean centerWindow = false;
            if (width != graphics.getLogicalWidth() || height != graphics.getLogicalHeight()) {
                centerWindow = true;
                Lwjgl3Graphics.Lwjgl3Monitor monitor = (Lwjgl3Graphics.Lwjgl3Monitor)graphics.getMonitor();
                GLFW.glfwGetMonitorWorkarea(monitor.monitorHandle, graphics.tmpBuffer, graphics.tmpBuffer2, graphics.tmpBuffer3, graphics.tmpBuffer4);
                newX = Math.max(0, graphics.tmpBuffer.get(0) + (graphics.tmpBuffer3.get(0) - width) / 2);
                newY = Math.max(0, graphics.tmpBuffer2.get(0) + (graphics.tmpBuffer4.get(0) - height) / 2);
            }
            GLFW.glfwSetWindowSize(window.getWindowHandle(), width, height);
            if (centerWindow) {
                window.setPosition(newX, newY); // on macOS the centering has to happen _after_ the new window size was set
            }
        } else {
            editorLogger.info("Attempting to transition to borderless fullscreen from a fullscreen window");
        }
        graphics.updateFramebufferInfo();
    }
}
