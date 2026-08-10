import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * {@link CapstoneDemo#runChain} narrates to stdout by design - it is a demo.
 * Tests want its {@link RunTrace}, not 80 lines of prose per run, so this
 * swallows stdout for the duration of the call.
 */
final class CapstoneDemoTestSupport {

    private CapstoneDemoTestSupport() {
    }

    static <T> T silently(Supplier<T> body) {
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
            return body.get();
        } finally {
            System.setOut(original);
        }
    }
}
