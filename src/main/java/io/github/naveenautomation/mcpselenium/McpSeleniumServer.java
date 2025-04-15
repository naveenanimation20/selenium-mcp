package io.github.naveenautomation.mcpselenium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class McpSeleniumServer {
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final Map<String, WebDriver> drivers = new ConcurrentHashMap<>();
	private static String currentSession = null;

	// Server info
	private static final String SERVER_NAME = "MCP Selenium";
	private static final String SERVER_VERSION = "1.0.0";

	public static void main(String[] args) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out))) {

			// Add shutdown hook for cleanup
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.err.println("Shutting down MCP Selenium Server...");
				drivers.forEach((sessionId, driver) -> {
					try {
						driver.quit();
					} catch (Exception e) {
						System.err.println("Error closing session " + sessionId + ": " + e.getMessage());
					}
				});
				drivers.clear();
			}));

			// Create server info response
			ObjectNode serverInfo = objectMapper.createObjectNode();
			serverInfo.put("name", SERVER_NAME);
			serverInfo.put("version", SERVER_VERSION);

			// List of available tools
			ArrayNode toolsArray = serverInfo.putArray("tools");

			// Define tools
			addTool(toolsArray, "start_browser", "launches browser", createStartBrowserSchema());
			addTool(toolsArray, "navigate", "navigates to a URL", createNavigateSchema());
			addTool(toolsArray, "find_element", "finds an element", createElementSchema());
			addTool(toolsArray, "click_element", "clicks an element", createElementSchema());
			addTool(toolsArray, "send_keys", "sends keys to an element, aka typing", createSendKeysSchema());
			addTool(toolsArray, "get_element_text", "gets the text() of an element", createElementSchema());
			addTool(toolsArray, "hover", "moves the mouse to hover over an element", createElementSchema());
			addTool(toolsArray, "drag_and_drop", "drags an element and drops it onto another element",
					createDragAndDropSchema());
			addTool(toolsArray, "double_click", "performs a double click on an element", createElementSchema());
			addTool(toolsArray, "right_click", "performs a right click (context click) on an element",
					createElementSchema());
			addTool(toolsArray, "press_key", "simulates pressing a keyboard key", createPressKeySchema());
			addTool(toolsArray, "upload_file", "uploads a file using a file input element", createUploadFileSchema());
			addTool(toolsArray, "take_screenshot", "captures a screenshot of the current page",
					createTakeScreenshotSchema());
			addTool(toolsArray, "close_session", "closes the current browser session", objectMapper.createObjectNode());

			// Send server info
			writer.write(serverInfo.toString());
			writer.newLine();
			writer.flush();

			String line;
			while ((line = reader.readLine()) != null) {
				JsonNode request = objectMapper.readTree(line);
				String type = request.get("type").asText();

				if ("tool_call".equals(type)) {
					String toolName = request.get("name").asText();
					JsonNode params = request.get("params");

					ObjectNode response = objectMapper.createObjectNode();
					response.put("type", "tool_response");
					response.put("tool_call_id", request.get("tool_call_id").asText());

					switch (toolName) {
					case "start_browser":
						response.set("content", startBrowser(params));
						break;
					case "navigate":
						response.set("content", navigate(params));
						break;
					case "find_element":
						response.set("content", findElement(params));
						break;
					case "click_element":
						response.set("content", clickElement(params));
						break;
					case "send_keys":
						response.set("content", sendKeys(params));
						break;
					case "get_element_text":
						response.set("content", getElementText(params));
						break;
					case "hover":
						response.set("content", hoverElement(params));
						break;
					case "drag_and_drop":
						response.set("content", dragAndDrop(params));
						break;
					case "double_click":
						response.set("content", doubleClick(params));
						break;
					case "right_click":
						response.set("content", rightClick(params));
						break;
					case "press_key":
						response.set("content", pressKey(params));
						break;
					case "upload_file":
						response.set("content", uploadFile(params));
						break;
					case "take_screenshot":
						response.set("content", takeScreenshot(params));
						break;
					case "close_session":
						response.set("content", closeSession());
						break;
					default:
						response.set("content", createErrorResponse("Unknown tool: " + toolName));
					}

					writer.write(response.toString());
					writer.newLine();
					writer.flush();
				} else if ("resource_request".equals(type)) {
					String uri = request.get("uri").asText();
					if (uri.startsWith("browser-status://")) {
						ObjectNode response = objectMapper.createObjectNode();
						response.put("type", "resource_response");
						response.put("request_id", request.get("request_id").asText());

						ArrayNode contents = objectMapper.createArrayNode();
						ObjectNode content = objectMapper.createObjectNode();
						content.put("uri", uri);
						content.put("text", currentSession != null ? "Active browser session: " + currentSession
								: "No active browser session");
						contents.add(content);

						response.set("contents", contents);
						writer.write(response.toString());
						writer.newLine();
						writer.flush();
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error in MCP server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static WebDriver getDriver() {
		if (currentSession == null || !drivers.containsKey(currentSession)) {
			throw new IllegalStateException("No active browser session");
		}
		return drivers.get(currentSession);
	}

	private static By getLocator(String by, String value) {
		switch (by.toLowerCase()) {
		case "id":
			return By.id(value);
		case "css":
			return By.cssSelector(value);
		case "xpath":
			return By.xpath(value);
		case "name":
			return By.name(value);
		case "tag":
			return By.tagName(value);
		case "class":
			return By.className(value);
		default:
			throw new IllegalArgumentException("Unsupported locator strategy: " + by);
		}
	}

	// Tool implementations
	private static ArrayNode startBrowser(JsonNode params) {
		try {
			String browser = params.get("browser").asText();
			JsonNode optionsNode = params.has("options") ? params.get("options") : null;
			boolean headless = optionsNode != null && optionsNode.has("headless")
					&& optionsNode.get("headless").asBoolean();
			List<String> arguments = new ArrayList<>();

			if (optionsNode != null && optionsNode.has("arguments")) {
				optionsNode.get("arguments").forEach(arg -> arguments.add(arg.asText()));
			}

			WebDriver driver;

			if ("chrome".equalsIgnoreCase(browser)) {
				ChromeOptions options = new ChromeOptions();
				if (headless) {
					options.addArguments("--headless=new");
				}
				for (String arg : arguments) {
					options.addArguments(arg);
				}

				driver = new ChromeDriver(options);
			} else if ("firefox".equalsIgnoreCase(browser)) {
				FirefoxOptions options = new FirefoxOptions();
				if (headless) {
					options.addArguments("--headless");
				}
				for (String arg : arguments) {
					options.addArguments(arg);
				}

				driver = new FirefoxDriver(options);
			} else {
				return createErrorResponse("Unsupported browser: " + browser);
			}

			String sessionId = browser + "_" + UUID.randomUUID().toString().replace("-", "");
			drivers.put(sessionId, driver);
			currentSession = sessionId;

			return createTextResponse("Browser started with session_id: " + sessionId);
		} catch (Exception e) {
			return createErrorResponse("Error starting browser: " + e.getMessage());
		}
	}

	private static ArrayNode navigate(JsonNode params) {
		try {
			String url = params.get("url").asText();
			WebDriver driver = getDriver();
			driver.get(url);
			return createTextResponse("Navigated to " + url);
		} catch (Exception e) {
			return createErrorResponse("Error navigating: " + e.getMessage());
		}
	}

	private static ArrayNode findElement(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			wait.until(ExpectedConditions.presenceOfElementLocated(locator));

			return createTextResponse("Element found");
		} catch (Exception e) {
			return createErrorResponse("Error finding element: " + e.getMessage());
		}
	}

	private static ArrayNode clickElement(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
			element.click();

			return createTextResponse("Element clicked");
		} catch (Exception e) {
			return createErrorResponse("Error clicking element: " + e.getMessage());
		}
	}

	private static ArrayNode sendKeys(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			String text = params.get("text").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
			element.clear();
			element.sendKeys(text);

			return createTextResponse("Text \"" + text + "\" entered into element");
		} catch (Exception e) {
			return createErrorResponse("Error entering text: " + e.getMessage());
		}
	}

	private static ArrayNode getElementText(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			String text = element.getText();

			return createTextResponse(text);
		} catch (Exception e) {
			return createErrorResponse("Error getting element text: " + e.getMessage());
		}
	}

	private static ArrayNode hoverElement(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));

			Actions actions = new Actions(driver);
			actions.moveToElement(element).perform();

			return createTextResponse("Hovered over element");
		} catch (Exception e) {
			return createErrorResponse("Error hovering over element: " + e.getMessage());
		}
	}

	private static ArrayNode dragAndDrop(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			String targetBy = params.get("targetBy").asText();
			String targetValue = params.get("targetValue").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By sourceLocator = getLocator(by, value);
			By targetLocator = getLocator(targetBy, targetValue);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement sourceElement = wait.until(ExpectedConditions.visibilityOfElementLocated(sourceLocator));
			WebElement targetElement = wait.until(ExpectedConditions.visibilityOfElementLocated(targetLocator));

			Actions actions = new Actions(driver);
			actions.dragAndDrop(sourceElement, targetElement).perform();

			return createTextResponse("Drag and drop completed");
		} catch (Exception e) {
			return createErrorResponse("Error performing drag and drop: " + e.getMessage());
		}
	}

	private static ArrayNode doubleClick(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));

			Actions actions = new Actions(driver);
			actions.doubleClick(element).perform();

			return createTextResponse("Double click performed");
		} catch (Exception e) {
			return createErrorResponse("Error performing double click: " + e.getMessage());
		}
	}

	private static ArrayNode rightClick(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));

			Actions actions = new Actions(driver);
			actions.contextClick(element).perform();

			return createTextResponse("Right click performed");
		} catch (Exception e) {
			return createErrorResponse("Error performing right click: " + e.getMessage());
		}
	}

	private static ArrayNode pressKey(JsonNode params) {
		try {
			String key = params.get("key").asText();
			WebDriver driver = getDriver();

			Actions actions = new Actions(driver);
			actions.sendKeys(key).perform();

			return createTextResponse("Key '" + key + "' pressed");
		} catch (Exception e) {
			return createErrorResponse("Error pressing key: " + e.getMessage());
		}
	}

	private static ArrayNode uploadFile(JsonNode params) {
		try {
			String by = params.get("by").asText();
			String value = params.get("value").asText();
			String filePath = params.get("filePath").asText();
			long timeout = params.has("timeout") ? params.get("timeout").asLong() : 10000;

			WebDriver driver = getDriver();
			By locator = getLocator(by, value);

			WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(timeout));
			WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
			element.sendKeys(filePath);

			return createTextResponse("File upload initiated");
		} catch (Exception e) {
			return createErrorResponse("Error uploading file: " + e.getMessage());
		}
	}

	private static ArrayNode takeScreenshot(JsonNode params) {
		try {
			WebDriver driver = getDriver();
			String outputPath = params.has("outputPath") ? params.get("outputPath").asText() : null;

			if (!(driver instanceof TakesScreenshot)) {
				return createErrorResponse("Current browser doesn't support taking screenshots");
			}

			TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
			String screenshot = screenshotDriver.getScreenshotAs(OutputType.BASE64);

			if (outputPath != null && !outputPath.isEmpty()) {
				Files.write(Paths.get(outputPath), Base64.getDecoder().decode(screenshot));
				return createTextResponse("Screenshot saved to " + outputPath);
			} else {
				ArrayNode content = objectMapper.createArrayNode();
				ObjectNode text1 = objectMapper.createObjectNode();
				text1.put("type", "text");
				text1.put("text", "Screenshot captured as base64:");
				content.add(text1);

				ObjectNode text2 = objectMapper.createObjectNode();
				text2.put("type", "text");
				text2.put("text", screenshot);
				content.add(text2);

				return content;
			}
		} catch (Exception e) {
			return createErrorResponse("Error taking screenshot: " + e.getMessage());
		}
	}

	private static ArrayNode closeSession() {
		try {
			if (currentSession != null) {
				WebDriver driver = drivers.get(currentSession);
				if (driver != null) {
					driver.quit();
					drivers.remove(currentSession);
				}

				String sessionId = currentSession;
				currentSession = null;
				return createTextResponse("Browser session " + sessionId + " closed");
			} else {
				return createErrorResponse("No active session to close");
			}
		} catch (Exception e) {
			return createErrorResponse("Error closing session: " + e.getMessage());
		}
	}

	// Helper methods for schema creation
	private static ObjectNode createStartBrowserSchema() {
		ObjectNode schema = objectMapper.createObjectNode();

		ObjectNode browserProp = objectMapper.createObjectNode();
		browserProp.put("type", "string");
		browserProp.put("enum", objectMapper.createArrayNode().add("chrome").add("firefox"));
		browserProp.put("description", "Browser to launch (chrome or firefox)");

		ObjectNode headlessProp = objectMapper.createObjectNode();
		headlessProp.put("type", "boolean");
		headlessProp.put("description", "Run browser in headless mode");

		ObjectNode argsProp = objectMapper.createObjectNode();
		argsProp.put("type", "array");
		argsProp.put("items", objectMapper.createObjectNode().put("type", "string"));
		argsProp.put("description", "Additional browser arguments");

		ObjectNode optionsProps = objectMapper.createObjectNode();
		optionsProps.set("headless", headlessProp);
		optionsProps.set("arguments", argsProp);

		ObjectNode optionsSchema = objectMapper.createObjectNode();
		optionsSchema.put("type", "object");
		optionsSchema.set("properties", optionsProps);
		optionsSchema.put("description", "Browser options");

		ObjectNode properties = objectMapper.createObjectNode();
		properties.set("browser", browserProp);
		properties.set("options", optionsSchema);

		schema.put("type", "object");
		schema.set("properties", properties);
		schema.set("required", objectMapper.createArrayNode().add("browser"));

		return schema;
	}

	private static ObjectNode createNavigateSchema() {
		ObjectNode schema = objectMapper.createObjectNode();

		ObjectNode urlProp = objectMapper.createObjectNode();
		urlProp.put("type", "string");
		urlProp.put("description", "URL to navigate to");

		ObjectNode properties = objectMapper.createObjectNode();
		properties.set("url", urlProp);

		schema.put("type", "object");
		schema.set("properties", properties);
		schema.set("required", objectMapper.createArrayNode().add("url"));

		return schema;
	}

	private static ObjectNode createElementSchema() {
		ObjectNode schema = objectMapper.createObjectNode();

		ObjectNode byProp = objectMapper.createObjectNode();
		byProp.put("type", "string");
		byProp.put("enum",
				objectMapper.createArrayNode().add("id").add("css").add("xpath").add("name").add("tag").add("class"));
		byProp.put("description", "Locator strategy to find element");

		ObjectNode valueProp = objectMapper.createObjectNode();
		valueProp.put("type", "string");
		valueProp.put("description", "Value for the locator strategy");

		ObjectNode timeoutProp = objectMapper.createObjectNode();
		timeoutProp.put("type", "number");
		timeoutProp.put("description", "Maximum time to wait for element in milliseconds");

		ObjectNode properties = objectMapper.createObjectNode();
		properties.set("by", byProp);
		properties.set("value", valueProp);
		properties.set("timeout", timeoutProp);

		schema.put("type", "object");
		schema.set("properties", properties);
		schema.set("required", objectMapper.createArrayNode().add("by").add("value"));

		return schema;
	}

	private static ObjectNode createSendKeysSchema() {
		ObjectNode schema = createElementSchema();
		ObjectNode properties = (ObjectNode) schema.get("properties");

		ObjectNode textProp = objectMapper.createObjectNode();
		textProp.put("type", "string");
		textProp.put("description", "Text to enter into the element");
		properties.set("text", textProp);

		((ArrayNode) schema.get("required")).add("text");

		return schema;
	}

	private static ObjectNode createDragAndDropSchema() {
		ObjectNode schema = createElementSchema();
		ObjectNode properties = (ObjectNode) schema.get("properties");

		ObjectNode targetByProp = objectMapper.createObjectNode();
		targetByProp.put("type", "string");
		targetByProp.put("enum",
				objectMapper.createArrayNode().add("id").add("css").add("xpath").add("name").add("tag").add("class"));
		targetByProp.put("description", "Locator strategy to find target element");

		ObjectNode targetValueProp = objectMapper.createObjectNode();
		targetValueProp.put("type", "string");
		targetValueProp.put("description", "Value for the target locator strategy");

		properties.set("targetBy", targetByProp);
		properties.set("targetValue", targetValueProp);

		((ArrayNode) schema.get("required")).add("targetBy").add("targetValue");

		return schema;
	}

	private static ObjectNode createPressKeySchema() {
		ObjectNode schema = objectMapper.createObjectNode();

		ObjectNode keyProp = objectMapper.createObjectNode();
		keyProp.put("type", "string");
		keyProp.put("description", "Key to press (e.g., 'Enter', 'Tab', 'a', etc.)");

		ObjectNode properties = objectMapper.createObjectNode();
		properties.set("key", keyProp);

		schema.put("type", "object");
		schema.set("properties", properties);
		schema.set("required", objectMapper.createArrayNode().add("key"));

		return schema;
	}

	private static ObjectNode createUploadFileSchema() {
		ObjectNode schema = createElementSchema();
		ObjectNode properties = (ObjectNode) schema.get("properties");

		ObjectNode filePathProp = objectMapper.createObjectNode();
		filePathProp.put("type", "string");
		filePathProp.put("description", "Absolute path to the file to upload");
		properties.set("filePath", filePathProp);

		((ArrayNode) schema.get("required")).add("filePath");

		return schema;
	}

	private static ObjectNode createTakeScreenshotSchema() {
		ObjectNode schema = objectMapper.createObjectNode();

		ObjectNode outputPathProp = objectMapper.createObjectNode();
		outputPathProp.put("type", "string");
		outputPathProp.put("description",
				"Optional path where to save the screenshot. If not provided, returns base64 data.");

		ObjectNode properties = objectMapper.createObjectNode();
		properties.set("outputPath", outputPathProp);

		schema.put("type", "object");
		schema.set("properties", properties);

		return schema;
	}

	// Helper methods for response creation
	private static ArrayNode createTextResponse(String text) {
		ArrayNode content = objectMapper.createArrayNode();
		ObjectNode textNode = objectMapper.createObjectNode();
		textNode.put("type", "text");
		textNode.put("text", text);
		content.add(textNode);
		return content;
	}

	private static ArrayNode createErrorResponse(String errorMessage) {
		return createTextResponse(errorMessage);
	}

	private static void addTool(ArrayNode tools, String name, String description, ObjectNode paramSchema) {
		ObjectNode tool = objectMapper.createObjectNode();
		tool.put("name", name);
		tool.put("description", description);
		tool.set("parameters", paramSchema);
		tools.add(tool);
	}
}
