import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallingDemoTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ObjectNode argsWithStatus(String status) {
        ObjectNode node = JSON.createObjectNode();
        if (status != null) node.put("status", status);
        return node;
    }

    @Test
    void executeToolReturnsOnlyOrdersMatchingStatus() {
        String result = ToolCallingDemo.executeTool("get_orders", argsWithStatus("open"));
        assertTrue(result.contains("\"Jansen\""));
        assertTrue(result.contains("\"de Vries\""));
        assertTrue(!result.contains("\"Bakker\"")); // status=shipped, moet niet meekomen
    }

    @Test
    void executeToolRejectsUnknownToolName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ToolCallingDemo.executeTool("delete_everything", argsWithStatus("open")));
        assertTrue(ex.getMessage().contains("onbekende tool"));
    }

    @Test
    void executeToolRejectsMissingStatusArgument() {
        JsonNode noStatus = JSON.createObjectNode();
        assertThrows(IllegalArgumentException.class,
                () -> ToolCallingDemo.executeTool("get_orders", noStatus));
    }

    @Test
    void executeToolRejectsHallucinatedStatus() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ToolCallingDemo.executeTool("get_orders", argsWithStatus("pending")));
        assertTrue(ex.getMessage().contains("pending"));
    }

    @Test
    void truncateLeavesShortStringsUntouched() {
        assertEquals("short", ToolCallingDemo.truncate("short", 300));
    }

    @Test
    void truncateCutsLongStringsAndAppendsEllipsis() {
        String longText = "x".repeat(50);
        String result = ToolCallingDemo.truncate(longText, 10);
        assertEquals("x".repeat(10) + "...", result);
    }
}
