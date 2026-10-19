package dev.chatads.eval.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VerdictParserTest {

    @Test
    void readsAWellFormedVerdict() throws Exception {
        VerdictParser.Verdict v = VerdictParser.parse(" {\"score\": 4, \"reason\": \"Right product type.\"}\n");
        assertEquals(4, v.score());
        assertEquals("Right product type.", v.reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "[4]",
            "{\"score\":4}",
            "{\"score\":4,\"reason\":\"ok\",\"extra\":1}",
            "{\"score\":\"4\",\"reason\":\"ok\"}",
            "{\"score\":4.0,\"reason\":\"ok\"}",
            "{\"score\":4.5,\"reason\":\"ok\"}",
            "{\"score\":0,\"reason\":\"ok\"}",
            "{\"score\":6,\"reason\":\"ok\"}",
            "{\"score\":4,\"reason\":\"\"}",
            "{\"score\":4,\"reason\":42}",
            "{\"score\":4,\"score\":5,\"reason\":\"ok\"}",
            "```json\n{\"score\":4,\"reason\":\"ok\"}\n```",
            "{\"score\":4,\"reason\":\"ok\"} thanks",
    })
    void rejectsAnythingThatIsNotExactlyTheSchema(String reply) {
        assertThrows(VerdictParser.FormatException.class, () -> VerdictParser.parse(reply));
    }

    @Test
    void nullReplyIsAFormatFailure() {
        assertThrows(VerdictParser.FormatException.class, () -> VerdictParser.parse(null));
    }
}
