import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagDemoTest {

    // -- chunkBySize -----------------------------------------------------

    @Test
    void chunkBySizeSplitsAtFixedCharBoundaries() {
        String content = "A".repeat(25) + "B".repeat(25) + "C".repeat(10);
        List<RagDemo.Chunk> chunks = RagDemo.chunkBySize(content, "src.java", 25);

        // last fragment (10 chars) is below the 20-char keep threshold and dropped
        assertEquals(2, chunks.size());
        assertEquals("A".repeat(25), chunks.get(0).text());
        assertEquals("B".repeat(25), chunks.get(1).text());
    }

    @Test
    void chunkBySizeDropsFragmentsAt20CharsOrBelow() {
        String content = "X".repeat(15);
        List<RagDemo.Chunk> chunks = RagDemo.chunkBySize(content, "src.java", 300);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chunkBySizeCarriesSourceThrough() {
        List<RagDemo.Chunk> chunks = RagDemo.chunkBySize("Y".repeat(30), "OllamaClient.java", 30);
        assertEquals("OllamaClient.java", chunks.get(0).source());
    }

    // -- chunkBySemantic ---------------------------------------------------

    @Test
    void chunkBySemanticFlushesAssoonAsBufferReaches100Chars() {
        String bigBlock = "a".repeat(150);
        List<RagDemo.Chunk> chunks = RagDemo.chunkBySemantic(bigBlock, "src.java");
        assertEquals(1, chunks.size());
        assertEquals(bigBlock, chunks.get(0).text());
    }

    @Test
    void chunkBySemanticMergesSmallBlocksAcrossBlankLines() {
        String blockA = "a".repeat(60);
        String blockB = "b".repeat(60);
        String content = blockA + "\n\n" + blockB;

        List<RagDemo.Chunk> chunks = RagDemo.chunkBySemantic(content, "src.java");

        // both blocks are under 100 chars alone -> merged into a single chunk
        // once the combined buffer crosses the 100-char flush threshold
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().contains(blockA));
        assertTrue(chunks.get(0).text().contains(blockB));
    }

    @Test
    void chunkBySemanticSkipsBlankBlocks() {
        String content = "\n\n\n\n" + "hello".repeat(30);
        List<RagDemo.Chunk> chunks = RagDemo.chunkBySemantic(content, "src.java");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().startsWith("hello"));
    }

    // -- cosine --------------------------------------------------------

    @Test
    void cosineOfIdenticalVectorsIsOne() {
        float[] v = {1.0f, 0.0f};
        assertEquals(1.0, RagDemo.cosine(v, v), 1e-9);
    }

    @Test
    void cosineOfOrthogonalVectorsIsZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertEquals(0.0, RagDemo.cosine(a, b), 1e-9);
    }

    @Test
    void cosineOfZeroVectorIsZeroNotNaN() {
        float[] zero = {0.0f, 0.0f};
        float[] other = {1.0f, 1.0f};
        assertEquals(0.0, RagDemo.cosine(zero, other), 1e-9);
    }
}
