package ipsnoop;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

import java.awt.image.BufferedImage;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;

import java.nio.file.Files;

import java.lang.reflect.InvocationTargetException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

public class IPSnoop {
    private static SystemTray systemTray;
    private static TrayIcon trayIcon;
    private static String currentIP = "";
    private static String lastKnownIP = "";
    private static boolean ipChanged = false;
    private static Timer hourlyTimer;
    private static ServerSocket singleInstanceSocket;
    private static volatile boolean aboutDialogShowing = false;
    private static final String VERSION = "1.3.1";
    private static final String BUILD = "633";
    
    private static boolean loadAtStartup = true; 
    
    
    public static void main(String[] args) {
        
        
	System.setProperty("apple.awt.UIElement", "true"); //
    
        if (!acquireInstanceLock()) {
            showAlreadyRunningNotification();
            System.exit(1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                releaseInstanceLock();
            }
        });
        
        if (!SystemTray.isSupported()) {
            System.err.println("System tray not supported!");
            return;
        }
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                createAndShowGUI();
            }
        });
    }

    private static boolean acquireInstanceLock() {
        try {
            
            singleInstanceSocket = new ServerSocket(49275, 1, InetAddress.getLoopbackAddress());
            
            
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!singleInstanceSocket.isClosed()) {
                            try (Socket socket = singleInstanceSocket.accept()) {
                                BufferedReader in = new BufferedReader(
                                        new InputStreamReader(socket.getInputStream()));
                                String command = in.readLine();
                                if ("show".equals(command)) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (trayIcon != null) {
                                                trayIcon.displayMessage("IPSnoop",
                                                        "IPSnoop is already running",
                                                        TrayIcon.MessageType.INFO);
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    } catch (IOException e) {
                        // Socket closed normally
                    }
                }
            }).start();
            
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void checkIP() {
        try {
            String previousIP = currentIP;
            lastKnownIP = currentIP;
            currentIP = getPublicIP();
            
            if (lastKnownIP.isEmpty()) {
                
                ipChanged = true;
                showNotification("IPSnoop", "Initial IP: " + currentIP);
            } else if (!currentIP.equals(lastKnownIP)) {
                ipChanged = !currentIP.equals(lastKnownIP);
                if (ipChanged) {
                    showNotification("IPSnoop", "IP Changed from " + previousIP + " to " + currentIP);
                    playPingSound();
                
                    
					
                }
            }
            
            updateTrayIcon();
            
        } catch (IOException e) {
            showNotification("IPSnoop Error", "Failed to check IP: " + e.getMessage());
        }
    }

    private static void confirmIP() {
        ipChanged = !currentIP.equals(lastKnownIP);
        lastKnownIP = currentIP;
        updateTrayIcon();
        showNotification("IPSnoop", "IP address confirmed: " + currentIP);
    }

    private static void configureMacOSAppSwitcher() {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            return;
        }

        try {
            
            Class<?> appClass = Class.forName("com.apple.eawt.Application");
            Object appInstance = appClass.getMethod("getApplication").invoke(null);
            Class<?> activationPolicyClass = Class.forName("com.apple.eawt.Application$ActivationPolicy");

            
            @SuppressWarnings("unchecked")
            Object accessoryPolicy = Enum.valueOf(
                (Class<Enum>) activationPolicyClass, 
                "ACCESSORY"
            );

            appClass.getMethod("setActivationPolicy", activationPolicyClass)
                   .invoke(appInstance, accessoryPolicy);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            System.err.println("Couldn't configure app switcher behavior: " + e.getMessage());
        }
    }

    private static void copyToClipboard() {
        StringSelection selection = new StringSelection(currentIP);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
        showNotification("IPSnoop", "\"" + currentIP + "\" copied to clipboard");
    }

    private static void createAndShowGUI() {
        try {
            
            systemTray = SystemTray.getSystemTray();
            
            final PopupMenu popup = new PopupMenu();
            
            MenuItem aboutItem = new MenuItem("About");
            MenuItem confirmItem = new MenuItem("Confirm IP Address");
            MenuItem checkNowItem = new MenuItem("Check IP Address");
            MenuItem copyItem = new MenuItem("Copy IP Address");
            Menu soundMenu = new Menu("Ping Sound");
            
            //MenuItem startupItem = new MenuItem("Load at Startup");
            final CheckboxMenuItem startupItem = new CheckboxMenuItem("Load at Startup");
            startupItem.setState(loadAtStartup);
            
            MenuItem quitItem = new MenuItem("Quit");
            MenuItem dockVisibilityItem = new MenuItem("Show in App Switcher");
            
            startupItem.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    loadAtStartup = startupItem.getState();
                    if (loadAtStartup) {
                        enableStartupLoading();
                    } else {
                        disableStartupLoading();
                    }
                }
            });
            dockVisibilityItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleAppSwitcherVisibility();
                }
            });
            
            String[] sounds = {"Ping", "Submarine", "Basso", "Pop"};
            for (final String sound : sounds) {
                MenuItem item = new MenuItem(sound);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        setPingSound(sound);
                    }
                });
                soundMenu.add(item);
            }
            
            
            popup.add(aboutItem);
            popup.addSeparator();
            popup.add(confirmItem);
            popup.add(checkNowItem);
            popup.add(copyItem);
            popup.addSeparator();
            popup.add(soundMenu);
            popup.addSeparator();
            
            //popup.add(startupItem);
            popup.add((MenuItem)startupItem);
            
            popup.add(dockVisibilityItem);
            popup.addSeparator();
            popup.add(quitItem);
            
           
            trayIcon = new TrayIcon(createTrayImageWithText("Checking..."));
            trayIcon.setImageAutoSize(true);
            
            
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        copyToClipboard();
                    }
                }
            });
            
           
            trayIcon.setPopupMenu(popup);
            
            
            aboutItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showAboutDialog(); 
                }
            });
            
            confirmItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ipChanged = false;
                    updateTrayIcon();
                    showNotification("IPSnoop", "IP address confirmed: " + currentIP);
                }
            });
            
            checkNowItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    checkIP();
                }
            });
            
            copyItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    copyToClipboard();
                }
            });
            
            quitItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    System.exit(0);
                }
            });
            
            
            systemTray.add(trayIcon);
            
            
            checkIP();
            startHourlyTimer();
            
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added: " + e.getMessage());
        }
    }

    private static void disableStartupLoading() {
        try {
            File plistFile = new File(System.getProperty("user.home"), 
                "Library/LaunchAgents/com.netbridgelimited.ipsnoop.plist");
            if (plistFile.exists()) {
                plistFile.delete();
            }
            showNotification("IPSnoop", "Application won't load at startup");
        } catch (Exception e) {
            System.err.println("Error disabling startup: " + e.getMessage());
        }
    }

    private static void enableStartupLoading() {
        try {
            // ~/Library/LaunchAgents/
            File launchAgentDir = new File(System.getProperty("user.home"), "Library/LaunchAgents");
            File plistFile = new File(launchAgentDir, "com.netbridgelimited.ipsnoop.plist");

            String plistContent = String.format(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                "<plist version=\"1.0\">\n" +
                "<dict>\n" +
                "    <key>Label</key>\n" +
                "    <string>com.netbridgelimited.ipsnoop</string>\n" +
                "    <key>ProgramArguments</key>\n" +
                "    <array>\n" +
                "        <string>%s</string>\n" +
                "        <string>-jar</string>\n" +
                "        <string>%s</string>\n" +
                "    </array>\n" +
                "    <key>RunAtLoad</key>\n" +
                "    <true/>\n" +
                "</dict>\n" +
                "</plist>",
                System.getProperty("java.home") + "/bin/java",
                new File("dist/IPSnoop.jar").getAbsolutePath()
            );

           
            Files.write(plistFile.toPath(), plistContent.getBytes());
            showNotification("IPSnoop", "Application will now load at startup");

        } catch (Exception e) {
            System.err.println("Error enabling startup: " + e.getMessage());
        }
    }

    private static Image createTrayImageWithText(String ip) {
        boolean isConfirmed = !lastKnownIP.isEmpty() && currentIP.equals(lastKnownIP);
        Image icon = loadImageResource(isConfirmed ? "green.png" : "red.png");

        // Create temporary graphics for text measurement
        BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG = tempImage.createGraphics();

        // Try to load SF Pro (macOS system font)
        Font font;
        try {
            font = Font.createFont(Font.TRUETYPE_FONT,
                    new File("/System/Library/Fonts/SFPro.ttf"))
                    .deriveFont(Font.PLAIN, 13);
        } catch (Exception e) {
            // Fallback fonts if SF Pro not available
            String[] fallbackFonts = {"SanFrancisco", "Helvetica Neue", "Arial", "Lucida Grande"};
            font = new Font("Arial", Font.PLAIN, 13); // Default fallback
            for (String fontName : fallbackFonts) {
                Font testFont = new Font(fontName, Font.PLAIN, 14);
                if (!testFont.getFamily().equals(Font.DIALOG)) {
                    font = testFont;
                    break;
                }
            }
        }

        tempG.setFont(font);
        FontMetrics metrics = tempG.getFontMetrics();
        int textWidth = metrics.stringWidth(ip);
        tempG.dispose();

        // Calculate image width (icon + padding + text width)
        int imageWidth = 20 + 5 + textWidth + 5; // icon(20) + padding(5) + text + padding(5)
        int imageHeight = 20; // Fixed height

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        
        float h = 0.33f;  // Hue (green)  
        float s = 0.9f;   // High saturation  
        float b = 0.5f;   // Low brightness (dark)  
        // Draw icon or fallback circle
        if (icon != null) {
            g.drawImage(icon, 2, 2, 25, 25, null);
        } else {
            g.setColor(ipChanged ? Color.RED : Color.getHSBColor(h, s, b));
            g.fillOval(2, 2, 15, 15);
        }

        // Draw text
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawString(ip, 25, 15); // Adjusted position for better centering

        g.dispose();
        return image;
    }
    /*
    private static Image createTrayImageWithText(String ip) {
        boolean isConfirmed = !lastKnownIP.isEmpty() && currentIP.equals(lastKnownIP);
        Image icon = loadImageResource(isConfirmed ? "green.png" : "red.png");
        BufferedImage image = new BufferedImage(150, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        if (icon != null) {
            g.drawImage(icon, 2, 2, 20, 20, null);
        } else {
            g.setColor(ipChanged ? Color.RED : Color.GREEN);
            g.fillOval(2, 2, 12, 12);
            g.setColor(Color.BLACK);

            Font sfPro;
            try {
                // SF Pro Text for regular weight (macOS 10.11+)
                sfPro = Font.createFont(Font.TRUETYPE_FONT,
                        new File("/System/Library/Fonts/SFPro.ttf"))
                        .deriveFont(Font.PLAIN, 13);
            } catch (Exception e) {
                // Fallback for other systems or if font not found
                sfPro = new Font("SanFrancisco", Font.PLAIN, 13); // Common macOS alias
            }
            
            

            g.setFont(sfPro);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawString(ip, 20, 12);

            g.dispose();
        }

        return image;
    }
    */
    private static Image loadImageResource(String filename) {
        try {
            URL url = IPSnoop.class.getResource("/resources/" + filename);
            if (url != null) {
                return Toolkit.getDefaultToolkit().getImage(url);
            }
        } catch (Exception e) {
            System.err.println("Error loading image: " + filename + " - " + e.getMessage());
        }
        return null;
    }

    
    

    private static String getPublicIP() throws IOException {
        URL ipService = new URL("http://checkip.amazonaws.com"); // https://api.ipify.org/
        try (BufferedReader in = new BufferedReader(new InputStreamReader(ipService.openStream()))) {
            return in.readLine();
        }
    }

    private static void playPingSound() {
        try {
            Runtime.getRuntime().exec(new String[]{"afplay", "/System/Library/Sounds/Ping.aiff"});
        } catch (IOException e) {
            Toolkit.getDefaultToolkit().beep();
        }
    }
    
    private static void releaseInstanceLock() {
        try {
            if (singleInstanceSocket != null) {
                singleInstanceSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error releasing instance lock: " + e.getMessage());
        }
    }

    
    
    private static void setPingSound(String soundName) {
        showNotification("IPSnoop", "Sound set to: " + soundName);
    }

    private static void showAboutDialog() {
        if (aboutDialogShowing) return;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                aboutDialogShowing = true;

               
                JDialog dialog = new JDialog((Frame)null, "About IPSnoop", true); 
                dialog.setAlwaysOnTop(true);
                
                
                JLabel message = new JLabel("<html><div style='text-align: center;'>"
                                            + "<b>IPSnoop</b> v" + VERSION + " (build " + BUILD + ")<br><br>"
                                            + "Monitors your public IP address<br>and notifies you of changes<br><br>"
                                            + " ©2007 netbridgelimited.com"
                                            + "</div></html>");
                message.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
                dialog.add(message);

                
                dialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        aboutDialogShowing = false;
                    }
                });

                dialog.pack();
                dialog.setLocationRelativeTo(null);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.setVisible(true);
            }
        });
    }
    
    private static void showAlreadyRunningNotification() {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), 49275); OutputStream out = socket.getOutputStream()) {
          
            out.write("show\n".getBytes());
        } catch (IOException e) {
        
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                try {
                    Runtime.getRuntime().exec(new String[]{"osascript", "-e", 
                        "display notification \"IPSnoop is already running\" " +
                        "with title \"IPSnoop\""});
                } catch (IOException ex) {
                    System.err.println("IPSnoop is already running");
                }
            }
        }
    }

    private static void showNotification(String title, String message) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                String script = String.format(
                    "display notification \"%s\" with title \"%s\"",
                    message.replace("\"", "\\\""),
                    title.replace("\"", "\\\"")
                );
                Runtime.getRuntime().exec(new String[]{"osascript", "-e", script});
            } catch (IOException e) {
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
            }
        } else {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    private static void startHourlyTimer() {
        hourlyTimer = new Timer();
        Calendar calendar = Calendar.getInstance();

        
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.HOUR_OF_DAY, 1);  // Move to next hour

        long initialDelay = calendar.getTimeInMillis() - System.currentTimeMillis();

        hourlyTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            @SuppressWarnings("unchecked")
            public void run() {
                if (ipChanged) {
                    playPingSound();
                }
                checkIP();
            }
        }, initialDelay, 60 * 60 * 1000);  
    }
    
    private static void startSocketListener() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!singleInstanceSocket.isClosed()) {
                        try (Socket socket = singleInstanceSocket.accept()) {
                            BufferedReader in = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));
                            String command = in.readLine();
                            if ("show".equals(command)) {
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        trayIcon.displayMessage("IPSnoop",
                                            "IPSnoop is already running",
                                            TrayIcon.MessageType.INFO);
                                    }
                                });
                            }
                        }
                    }
                } catch (IOException e) {
                    // Socket closed normally
                }
            }
        }).start();
    }
    
    private static void toggleAppSwitcherVisibility() {
        try {
            Class<?> appClass = Class.forName("com.apple.eawt.Application");
            Object app = appClass.getMethod("getApplication").invoke(null);

            
            Class<?> activationPolicyClass = Class.forName("com.apple.eawt.Application$ActivationPolicy");

            
            //Object[] policies = activationPolicyClass.getEnumConstants();
            @SuppressWarnings("unchecked")
            Enum<?>[] policies = (Enum<?>[]) activationPolicyClass.getEnumConstants();
            Object accessoryPolicy = null;
            for (Object policy : policies) {
                if ("ACCESSORY".equals(policy.toString())) {
                    accessoryPolicy = policy;
                    break;
                }
            }

            if (accessoryPolicy != null) {
                appClass.getMethod("setActivationPolicy", activationPolicyClass)
                       .invoke(app, accessoryPolicy);
            }
        } catch (Exception ex) {
            System.err.println("Couldn't configure app switcher behavior: " + ex.getMessage());
        }
    }
    
    private static void updateTrayIcon() {
        trayIcon.setImage(createTrayImageWithText(currentIP));
        trayIcon.setToolTip("IPSnoop - " + currentIP + (ipChanged ? " (Unconfirmed)" : ""));
    }

}

