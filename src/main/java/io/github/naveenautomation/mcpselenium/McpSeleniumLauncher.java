package io.github.naveenautomation.mcpselenium;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author naveenautomationlabs
 *	Date: 15th Apr 2025
 */

/**
 * Launcher for the MCP Selenium Server
 * This class provides a simple way to start the server from the command line
 */
public class McpSeleniumLauncher {
    
    public static void main(String[] args) {
        try {
            // Build the command to run the server
            List<String> command = new ArrayList<>();
            
            // Get the Java executable path
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            
            command.add(javaBin);
            
            // Set classpath to the current classpath
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            
            // Add main class
            command.add(McpSeleniumServer.class.getName());
            
            // Process builder
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            
            // Redirect I/O to inherit from this process
            processBuilder.inheritIO();
            
            // Start the process
            Process process = processBuilder.start();
            
            // Add shutdown hook to ensure clean termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (process.isAlive()) {
                    System.out.println("Shutting down MCP Selenium Server...");
                    process.destroy();
                }
            }));
            
            // Wait for the process to complete
            int exitCode = process.waitFor();
            System.exit(exitCode);
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error starting MCP Selenium Server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}