package alchyr;

import javax.swing.*;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.*;

public class Updater {
    private static final String TEMP = "temp.jar";
    private static final String ACTIVE = "desktop-1.0.jar";
    private static final String OLD = "oldVer.jar";
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("UPDATE DATA NOT PROVIDED");
            JOptionPane.showMessageDialog(null, "Error: Update data not provided.", "Taiko Editor", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            try {
                Thread.currentThread().setName("Taiko Editor Updater");
            }
            catch (Exception ignored) { }
            try {
                Thread.sleep(2500);
            }
            catch (Exception ignored) { }

            String location = args[0];
            System.out.println("Destination: " + location);
            //JOptionPane.showMessageDialog(null, "Destination: " + location, "Taiko Editor", JOptionPane.INFORMATION_MESSAGE);

            Path destination = Paths.get(location);
            if (!Files.exists(destination)) {
                System.out.println("Failed to update. Expected directory did not exist.");
                JOptionPane.showMessageDialog(null, "Error: lib folder does not exist or is not in expected location.", "Taiko Editor", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Path updatePath = Paths.get(location, ACTIVE);
            Path tempPath = Paths.get(location, TEMP);
            Path backupPath = Paths.get(location, OLD);

            int attempts = 3;
            while (attempts > 0) {
                try {
                    --attempts;
                    if (Files.exists(updatePath)) {
                        Files.move(updatePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                catch (Exception e) {
                    if (attempts > 2) {
                        JOptionPane.showMessageDialog(null, "Error: Failed to move old version. " + e.getMessage() + "Waiting to try again (" + attempts + " tries left).", "Taiko Editor", JOptionPane.ERROR_MESSAGE);
                    }
                    else if (attempts == 1) {
                        JOptionPane.showMessageDialog(null, "Error: Failed to move old version. " + e.getMessage() + "Waiting to try again (1 try left).", "Taiko Editor", JOptionPane.ERROR_MESSAGE);
                    }
                    else {
                        JOptionPane.showMessageDialog(null, "Error: Failed to move old version. " + e.getMessage(), "Taiko Editor", JOptionPane.ERROR_MESSAGE);
                    }
                    Thread.sleep(2000);
                }
            }

            if (Files.exists(tempPath)) {
                //JOptionPane.showMessageDialog(null, "Moving update file", "Taiko Editor", JOptionPane.INFORMATION_MESSAGE);
                Files.move(tempPath, updatePath, StandardCopyOption.REPLACE_EXISTING);
            }
            else {
                System.out.println("Failed to update. Updated version file not found.");
                JOptionPane.showMessageDialog(null, "Error: Updated version \"temp.jar\" not found in lib folder.", "Taiko Editor", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JOptionPane.showMessageDialog(null, "Updated successfully.", "Taiko Editor", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (NoSuchFileException e) {
            System.out.println("Target folder did not exist.");
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error: Target folder did not exist.", "Taiko Editor", JOptionPane.INFORMATION_MESSAGE);

            try {
                File f = new File("updateError.txt");
                PrintWriter pWriter = null;

                try {
                    pWriter = new PrintWriter(f);
                    e.printStackTrace(pWriter);
                }
                catch (Exception ex) {
                    Thread.sleep(3000);
                }
                finally {
                    if (pWriter != null)
                        pWriter.close();
                }
            }
            catch (Exception ignored) {

            }
        }
        catch (Exception e) {
            System.out.println("Update process failed.");
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to update. " + e.getMessage(), "Taiko Editor", JOptionPane.INFORMATION_MESSAGE);

            try {
                File f = new File("updateError.txt");
                PrintWriter pWriter = null;

                try {
                    pWriter = new PrintWriter(f);
                    e.printStackTrace(pWriter);
                }
                catch (Exception ex) {
                    Thread.sleep(3000);
                }
                finally {
                    if (pWriter != null)
                        pWriter.close();
                }
            }
            catch (Exception ignored) {

            }
        }
    }
}
