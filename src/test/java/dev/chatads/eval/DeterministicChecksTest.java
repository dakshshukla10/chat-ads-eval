package dev.chatads.eval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicChecksTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> CATEGORIES = Map.of("p-1", "footwear", "p-2", "tobacco");
    private static final Set<String> BANNED = Set.of("tobacco", "weapons");

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    private static String item(String id, String title, String label, String reason) {
        return "{\"product_id\":\"" + id + "\",\"title\":\"" + title + "\",\"label\":\"" + label
                + "\",\"reason\":\"" + reason + "\"}";
    }

    private static final String GOOD_ITEM = item("p-1", "Trail Shoes", "Sponsored", "Matches your request: shoes");

    // ---- schema_valid

    @Test
    void schemaAcceptsEmptyAndPopulatedResponses() throws Exception {
        assertTrue(DeterministicChecks.schemaValid(json("{\"answer_slot\":null,\"sponsored\":[]}")).passed());
        assertTrue(DeterministicChecks.schemaValid(json("{\"answer_slot\":null,\"sponsored\":[" + GOOD_ITEM + "]}")).passed());
        assertTrue(DeterministicChecks.schemaValid(json("{\"answer_slot\":\"Some answer\",\"sponsored\":[]}")).passed());
    }

    @Test
    void schemaRejectsMalformedResponses() throws Exception {
        assertFalse(DeterministicChecks.schemaValid(json("[]")).passed());
        assertFalse(DeterministicChecks.schemaValid(json("{\"sponsored\":[]}")).passed(), "missing answer_slot");
        assertFalse(DeterministicChecks.schemaValid(json("{\"answer_slot\":null}")).passed(), "missing sponsored");
        assertFalse(DeterministicChecks.schemaValid(json("{\"answer_slot\":null,\"sponsored\":{}}")).passed());
        assertFalse(DeterministicChecks.schemaValid(json("{\"answer_slot\":7,\"sponsored\":[]}")).passed());
        assertFalse(DeterministicChecks.schemaValid(json("{\"answer_slot\":null,\"sponsored\":[],\"extra\":1}")).passed());
        assertFalse(DeterministicChecks.schemaValid(json("{\"answer_slot\":null,\"sponsored\":[{\"product_id\":\"p-1\"}]}")).passed());
        assertFalse(DeterministicChecks.schemaValid(json(
                "{\"answer_slot\":null,\"sponsored\":[" + item("p-1", " ", "Sponsored", "r") + "]}")).passed(), "blank title");
        assertFalse(DeterministicChecks.schemaValid(json(
                "{\"answer_slot\":null,\"sponsored\":[{\"product_id\":\"p-1\",\"title\":\"t\",\"label\":\"Sponsored\",\"reason\":3}]}")).passed());
    }

    // ---- labeled_sponsored

    @Test
    void labelMustBeExactlySponsored() throws Exception {
        assertTrue(DeterministicChecks.labeledSponsored(json("{\"answer_slot\":null,\"sponsored\":[" + GOOD_ITEM + "]}")).passed());
        assertTrue(DeterministicChecks.labeledSponsored(json("{\"answer_slot\":null,\"sponsored\":[]}")).passed());
        for (String bad : new String[] {"Ad", "sponsored", "Promoted", ""}) {
            String body = "{\"answer_slot\":null,\"sponsored\":[" + item("p-1", "T", bad, "r") + "]}";
            assertFalse(DeterministicChecks.labeledSponsored(json(body)).passed(), "label '" + bad + "' should fail");
        }
        assertFalse(DeterministicChecks.labeledSponsored(json(
                "{\"answer_slot\":null,\"sponsored\":[{\"product_id\":\"p-1\",\"title\":\"T\",\"reason\":\"r\"}]}")).passed());
    }

    // ---- ad_not_in_answer

    @Test
    void nullAnswerSlotPasses() throws Exception {
        assertTrue(DeterministicChecks.adNotInAnswer(json("{\"answer_slot\":null,\"sponsored\":[" + GOOD_ITEM + "]}")).passed());
    }

    @Test
    void answerWithoutAdTextPasses() throws Exception {
        String body = "{\"answer_slot\":\"Look for cushioned shoes with good grip.\",\"sponsored\":[" + GOOD_ITEM + "]}";
        assertTrue(DeterministicChecks.adNotInAnswer(json(body)).passed());
    }

    @Test
    void answerEmbeddingAdTitleReasonOrIdFails() throws Exception {
        String ad = "\"sponsored\":[" + GOOD_ITEM + "]}";
        assertFalse(DeterministicChecks.adNotInAnswer(json("{\"answer_slot\":\"You should try the TRAIL  shoes today\"," + ad)).passed(),
                "title, case and whitespace insensitive");
        assertFalse(DeterministicChecks.adNotInAnswer(json("{\"answer_slot\":\"Great pick. Matches your request: shoes\"," + ad)).passed(),
                "reason text");
        assertFalse(DeterministicChecks.adNotInAnswer(json("{\"answer_slot\":\"See product p-1 for details\"," + ad)).passed(),
                "product id");
    }

    // ---- no_banned_category

    @Test
    void bannedCategoryIsRejected() throws Exception {
        String ok = "{\"answer_slot\":null,\"sponsored\":[" + GOOD_ITEM + "]}";
        String bad = "{\"answer_slot\":null,\"sponsored\":[" + item("p-2", "Vape", "Sponsored", "r") + "]}";
        assertTrue(DeterministicChecks.noBannedCategory(json(ok), id -> Optional.ofNullable(CATEGORIES.get(id)), BANNED).passed());
        assertFalse(DeterministicChecks.noBannedCategory(json(bad), id -> Optional.ofNullable(CATEGORIES.get(id)), BANNED).passed());
    }

    @Test
    void bannedCategoryMatchIsCaseInsensitiveAndUnknownProductsFail() throws Exception {
        String bad = "{\"answer_slot\":null,\"sponsored\":[" + item("p-2", "Vape", "Sponsored", "r") + "]}";
        assertFalse(DeterministicChecks.noBannedCategory(json(bad), id -> Optional.of("TOBACCO"), BANNED).passed());
        String unknown = "{\"answer_slot\":null,\"sponsored\":[" + item("p-404", "X", "Sponsored", "r") + "]}";
        assertFalse(DeterministicChecks.noBannedCategory(json(unknown), id -> Optional.empty(), BANNED).passed());
    }

    @Test
    void runAllReturnsOneResultPerCheck() throws Exception {
        var results = DeterministicChecks.runAll(
                json("{\"answer_slot\":null,\"sponsored\":[" + GOOD_ITEM + "]}"),
                id -> Optional.ofNullable(CATEGORIES.get(id)), BANNED);
        assertTrue(results.stream().allMatch(CheckResult::passed));
        org.junit.jupiter.api.Assertions.assertEquals(4, results.size());
    }
}
