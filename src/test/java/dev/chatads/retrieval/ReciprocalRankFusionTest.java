package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void scoreIsTheSumOfReciprocalRanks() {
        Map<String, Double> fused = ReciprocalRankFusion.fuse(List.of(List.of("a", "b"), List.of("b", "c")), 60);
        assertEquals(1.0 / 61, fused.get("a"), 1e-12);
        assertEquals(1.0 / 62 + 1.0 / 61, fused.get("b"), 1e-12);
        assertEquals(1.0 / 62, fused.get("c"), 1e-12);
    }

    @Test
    void aDocumentInBothListsBeatsATopHitInOne() {
        Map<String, Double> fused = ReciprocalRankFusion.fuse(List.of(List.of("solo", "both"), List.of("both")), 60);
        assertTrue(fused.get("both") > fused.get("solo"));
    }

    @Test
    void emptyListsFuseToNothing() {
        assertTrue(ReciprocalRankFusion.fuse(List.of(List.of(), List.of()), 60).isEmpty());
    }
}
