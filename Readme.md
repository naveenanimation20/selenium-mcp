# MCP Selenium

A Java implementation of the Model Context Protocol (MCP) for Selenium WebDriver, enabling browser automation through standardized MCP clients.

## Overview

MCP Selenium provides a bridge between the Model Context Protocol and Selenium WebDriver. It allows AI assistants and other MCP-compatible clients to perform browser automation tasks using a standardized set of tools.

## Project Structure

```
selenium-mcp/
├── src/
│   └── main/
│       └── java/
│           └── io/
│               └── github/
│                   └── naveenautomation/
│                       └── mcpselenium/
│                           ├── McpSeleniumServer.java       # The main MCP server implementation
│                           ├── McpSeleniumLauncher.java     # Launcher script for the server
│                           └── AdvancedMcpClient.java       # GUI client for testing
├── target/
│   └── mcp-selenium-0.1.0-jar-with-dependencies.jar         # Compiled JAR with dependencies
└── pom.xml                                                  # Maven project configuration
```

## Prerequisites

- Java 11 or higher
- Maven (for building)
- Chrome or Firefox browser installed

## Building the Project

```bash
# Clone the repository
git clone https://github.com/naveenautomation/selenium-mcp.git
cd selenium-mcp

# Build with Maven
mvn clean package
```

This will create the JAR file at `target/mcp-selenium-0.1.0-jar-with-dependencies.jar`.

## Usage Options

### Option 1: Command Line Usage

#### Starting the Server

Start the MCP Selenium server from the command line:

```bash
java -jar target/mcp-selenium-0.1.0-jar-with-dependencies.jar
```

The server will start and wait for commands on standard input. You'll see the server information as initial output.

#### Sending Commands

You can now send JSON commands to the server. Type or paste the commands directly into the terminal, one per line.

##### Example Commands

1. Start a Chrome browser:
```json
{"type":"tool_call","tool_call_id":"call-1","name":"start_browser","params":{"browser":"chrome","options":{"headless":false}}}
```

2. Navigate to a website:
```json
{"type":"tool_call","tool_call_id":"call-2","name":"navigate","params":{"url":"https://www.example.com"}}
```

3. Find an element:
```json
{"type":"tool_call","tool_call_id":"call-3","name":"find_element","params":{"by":"css","value":"h1"}}
```

4. Get element text:
```json
{"type":"tool_call","tool_call_id":"call-4","name":"get_element_text","params":{"by":"css","value":"h1"}}
```

5. Click an element:
```json
{"type":"tool_call","tool_call_id":"call-5","name":"click_element","params":{"by":"id","value":"submit-button"}}
```

6. Type text into an element:
```json
{"type":"tool_call","tool_call_id":"call-6","name":"send_keys","params":{"by":"id","value":"search-box","text":"search query"}}
```

7. Take a screenshot:
```json
{"type":"tool_call","tool_call_id":"call-7","name":"take_screenshot","params":{"outputPath":"screenshot.png"}}
```

8. Close the browser:
```json
{"type":"tool_call","tool_call_id":"call-8","name":"close_session","params":{}}
```

#### Closing the Server

To stop the server, use Ctrl+C in the terminal.

### Option 2: GUI Client

For a more user-friendly experience, you can use the included GUI client.

#### Starting the GUI Client

Compile and run the client:

```bash
# Compile the client
javac -d target/classes src/main/java/io/github/naveenautomation/mcpselenium/AdvancedMcpClient.java

# Run the client
java -cp target/classes io.github.naveenautomation.mcpselenium.AdvancedMcpClient
```

#### Using the GUI

The GUI client automatically starts the MCP Selenium server when launched. The interface includes:

1. **Command Selector**: Dropdown menu to select the MCP command you want to execute.
2. **JSON Parameters**: Text field to enter command parameters in JSON format.
3. **Quick Action Buttons**: 
   - Start Chrome: Launches Chrome browser
   - Navigate: Opens a dialog to enter a URL
   - Screenshot: Takes a screenshot
   - Close Browser: Closes the current session
4. **Log Area**: Displays server responses and logs.

#### Examples of GUI Usage

##### Find Element

1. Select "find_element" from the command dropdown.
2. Enter parameters in the JSON field:
```json
{"by":"id","value":"login-button"}
```
3. Click "Send".

##### Send Keys (Type Text)

1. Select "send_keys" from the command dropdown.
2. Enter parameters in the JSON field:
```json
{"by":"id","value":"username","text":"testuser@example.com"}
```
3. Click "Send".

##### Click Element

1. Select "click_element" from the command dropdown.
2. Enter parameters in the JSON field:
```json
{"by":"css","value":"button.submit"}
```
3. Click "Send".

## Supported Commands

The MCP Selenium Server supports the following commands:

| Command | Description | Required Parameters | Optional Parameters |
|---------|-------------|---------------------|---------------------|
| `start_browser` | Launches a browser | `browser` ("chrome" or "firefox") | `options.headless`, `options.arguments` |
| `navigate` | Navigates to a URL | `url` | - |
| `find_element` | Finds an element | `by`, `value` | `timeout` |
| `click_element` | Clicks an element | `by`, `value` | `timeout` |
| `send_keys` | Types text into an element | `by`, `value`, `text` | `timeout` |
| `get_element_text` | Gets text from an element | `by`, `value` | `timeout` |
| `hover` | Hovers over an element | `by`, `value` | `timeout` |
| `drag_and_drop` | Drags and drops an element | `by`, `value`, `targetBy`, `targetValue` | `timeout` |
| `double_click` | Double-clicks an element | `by`, `value` | `timeout` |
| `right_click` | Right-clicks an element | `by`, `value` | `timeout` |
| `press_key` | Presses a keyboard key | `key` | - |
| `upload_file` | Uploads a file | `by`, `value`, `filePath` | `timeout` |
| `take_screenshot` | Takes a screenshot | - | `outputPath` |
| `close_session` | Closes the browser | - | - |

## Locator Strategies

For commands that interact with elements, use these locator strategies in the `by` parameter:

- `id`: Element ID
- `css`: CSS selector
- `xpath`: XPath expression
- `name`: Element name
- `tag`: HTML tag name
- `class`: CSS class name

## Integration with AI Systems

MCP Selenium is designed to be used with AI systems that support the Model Context Protocol. To integrate with an AI assistant like Claude:

1. Start the MCP Selenium server
2. Configure the AI system to connect to the server via stdin/stdout
3. Send natural language commands to the AI, which will translate them to MCP commands

## Troubleshooting

### Common Issues

1. **Browser Not Starting**:
   - Ensure you have Chrome or Firefox installed
   - Try using `"headless":true` in options
   - Check server logs for detailed error messages

2. **Element Not Found**:
   - Verify your locator (by and value)
   - Increase the timeout value
   - Check if the element is in an iframe

3. **Server Not Responding**:
   - Ensure the JSON format is correct
   - Check that each command has a unique tool_call_id
   - Restart the server if it becomes unresponsive

4. **Screenshot Not Saving**:
   - Provide an absolute file path
   - Ensure the directory exists
   - Check file permissions

## License

This project is licensed under the MIT License.

## Acknowledgements

- Built on Selenium WebDriver for browser automation
- Implements the Model Context Protocol standard
