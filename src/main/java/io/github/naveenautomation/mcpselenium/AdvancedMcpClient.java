package io.github.naveenautomation.mcpselenium;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class AdvancedMcpClient extends JFrame {
	private static final String JAR_PATH = "target/selenium-mcp-0.1.0-jar-with-dependencies.jar"; // Update this path
	private Process serverProcess;
	private BufferedWriter serverInput;
	private BufferedReader serverOutput;
	private final AtomicInteger callIdCounter = new AtomicInteger(1);

	private final JTextArea logArea;
	private final JTextField commandField;
	private final JComboBox<String> commandSelector;

	public AdvancedMcpClient() {
		super("MCP Selenium Client");
		setSize(800, 600);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Add window close handler to clean up
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				stopServer();
			}
		});

		// Create UI components
		JPanel mainPanel = new JPanel(new BorderLayout());

		// Command selector
		String[] commands = { "start_browser", "navigate", "find_element", "click_element", "send_keys",
				"get_element_text", "hover", "drag_and_drop", "double_click", "right_click", "press_key", "upload_file",
				"take_screenshot", "close_session" };
		commandSelector = new JComboBox<>(commands);

		// Command input
		commandField = new JTextField();
		JButton sendButton = new JButton("Send");
		sendButton.addActionListener(e -> sendCommand());

		// Top panel for command entry
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(new JLabel("Command: "), BorderLayout.WEST);
		topPanel.add(commandSelector, BorderLayout.CENTER);

		// Middle panel for params
		JPanel paramPanel = new JPanel(new BorderLayout());
		paramPanel.add(new JLabel("JSON Params: "), BorderLayout.WEST);
		paramPanel.add(commandField, BorderLayout.CENTER);
		paramPanel.add(sendButton, BorderLayout.EAST);

		// Command composite panel
		JPanel commandPanel = new JPanel(new GridLayout(2, 1));
		commandPanel.add(topPanel);
		commandPanel.add(paramPanel);

		// Quick action buttons
		JPanel quickActionPanel = new JPanel(new FlowLayout());
		JButton chromeButton = new JButton("Start Chrome");
		chromeButton.addActionListener(e -> startChrome());

		JButton navigateButton = new JButton("Navigate");
		navigateButton.addActionListener(e -> navigate());

		JButton closeButton = new JButton("Close Browser");
		closeButton.addActionListener(e -> closeBrowser());

		JButton screenshotButton = new JButton("Screenshot");
		screenshotButton.addActionListener(e -> takeScreenshot());

		quickActionPanel.add(chromeButton);
		quickActionPanel.add(navigateButton);
		quickActionPanel.add(screenshotButton);
		quickActionPanel.add(closeButton);

		// Log area
		logArea = new JTextArea();
		logArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(logArea);

		// Add components to main panel
		mainPanel.add(commandPanel, BorderLayout.NORTH);
		mainPanel.add(quickActionPanel, BorderLayout.SOUTH);
		mainPanel.add(scrollPane, BorderLayout.CENTER);

		// Set content pane
		setContentPane(mainPanel);
	}

	private void log(String message) {
		SwingUtilities.invokeLater(() -> {
			logArea.append(message + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		});
	}

	private void startServer() {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", JAR_PATH);
			serverProcess = processBuilder.start();

			serverInput = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream()));
			serverOutput = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));

			// Start a thread to read server responses
			new Thread(() -> {
				try {
					String response;
					// Read the initial server info
					response = serverOutput.readLine();
					log("Server info: " + response);

					// Continue reading responses
					while ((response = serverOutput.readLine()) != null) {
						log("Response: " + response);
					}
				} catch (IOException e) {
					if (serverProcess == null || !serverProcess.isAlive()) {
						log("Server process has terminated");
					} else {
						log("Error reading from server: " + e.getMessage());
						e.printStackTrace();
					}
				}
			}).start();

			// Start a thread to handle server errors
			new Thread(() -> {
				try (BufferedReader errorReader = new BufferedReader(
						new InputStreamReader(serverProcess.getErrorStream()))) {
					String line;
					while ((line = errorReader.readLine()) != null) {
						log("Server Error: " + line);
					}
				} catch (IOException e) {
					log("Error reading server errors: " + e.getMessage());
					e.printStackTrace();
				}
			}).start();

			log("Server started");

		} catch (IOException e) {
			log("Error starting server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void stopServer() {
		try {
			if (serverInput != null)
				serverInput.close();
			if (serverOutput != null)
				serverOutput.close();
			if (serverProcess != null && serverProcess.isAlive()) {
				serverProcess.destroy();
				log("Server stopped");
			}
		} catch (IOException e) {
			log("Error stopping server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void sendCommand() {
		String command = (String) commandSelector.getSelectedItem();
		String params = commandField.getText().trim();

		if (params.isEmpty()) {
			params = "{}";
		}

		// Make sure params starts with { and ends with }
		if (!params.startsWith("{")) {
			params = "{" + params;
		}
		if (!params.endsWith("}")) {
			params = params + "}";
		}

		String jsonCommand = String.format(
				"{\"type\":\"tool_call\",\"tool_call_id\":\"call-%d\",\"name\":\"%s\",\"params\":%s}",
				callIdCounter.getAndIncrement(), command, params);

		sendRawCommand(jsonCommand);
	}

	private void sendRawCommand(String jsonCommand) {
		if (serverInput == null) {
			log("Server not started. Cannot send command.");
			return;
		}

		try {
			log("Sending: " + jsonCommand);
			serverInput.write(jsonCommand);
			serverInput.newLine();
			serverInput.flush();
		} catch (IOException e) {
			log("Error sending command: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Quick action methods

	private void startChrome() {
		String jsonCommand = String.format(
				"{\"type\":\"tool_call\",\"tool_call_id\":\"call-%d\",\"name\":\"start_browser\",\"params\":{\"browser\":\"chrome\",\"options\":{\"headless\":false}}}",
				callIdCounter.getAndIncrement());
		sendRawCommand(jsonCommand);
	}

	private void navigate() {
		String url = JOptionPane.showInputDialog(this, "Enter URL:", "https://www.example.com");
		if (url != null && !url.isEmpty()) {
			String jsonCommand = String.format(
					"{\"type\":\"tool_call\",\"tool_call_id\":\"call-%d\",\"name\":\"navigate\",\"params\":{\"url\":\"%s\"}}",
					callIdCounter.getAndIncrement(), url);
			sendRawCommand(jsonCommand);
		}
	}

	private void takeScreenshot() {
		String outputPath = JOptionPane.showInputDialog(this, "Save screenshot to (leave empty for base64):",
				"screenshot.png");
		String jsonCommand;

		if (outputPath == null || outputPath.isEmpty()) {
			jsonCommand = String.format(
					"{\"type\":\"tool_call\",\"tool_call_id\":\"call-%d\",\"name\":\"take_screenshot\",\"params\":{}}",
					callIdCounter.getAndIncrement());
		} else {
			jsonCommand = String.format(
					"{\"type\":\"tool_call\",\"tool_call_id\":\"call-%d\",\"name\":\"take_screenshot\",\"params\":{\"outputPath\":\"%s\"}}",
					callIdCounter.getAndIncrement(), outputPath);
		}

		sendRawCommand(jsonCommand);
	}

	private void closeBrowser() {
		String jsonCommand = String.format(
				"{\"type\":\"tool_call\",\"tool_call_id\":\"call-%d\",\"name\":\"close_session\",\"params\":{}}",
				callIdCounter.getAndIncrement());
		sendRawCommand(jsonCommand);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			AdvancedMcpClient client = new AdvancedMcpClient();
			client.setVisible(true);
			client.startServer();
		});
	}
}
