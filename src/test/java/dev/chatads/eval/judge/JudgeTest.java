package dev.chatads.eval.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chatads.llm.TokenUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeTest {

    /** Replays canned replies and remembers what it was asked. */
    private static final class FakeClient implements JudgeClient {
        final Deque<String> replies = new ArrayDeque<>();
        final List<String> systems = new ArrayList<>();
        final List<String> users = new ArrayList<>();

        FakeClient(String... replies) {
            this.replies.addAll(List.of(replies));
        }

        @Override
        public Completion complete(String systemPrompt, String userMessage) {
            systems.add(systemPrompt);
            users.add(userMessage);
            return new Completion(replies.removeFirst(), new TokenUsage(100, 10, false));
        }

        @Override
        public String model() {
            return "fake";
        }
    }

    private static final Rubric RUBRIC = new Rubric(JudgeDimension.RELEVANCE, 1, "<!-- rubric: relevance version: 1 -->\nRate it.");
    private static final JudgeInput INPUT = new JudgeInput("running shoes", null,
            new JudgeInput.Ad("Trailblaze Running Shoes", "footwear", "Sponsored", "Matches your request: running"));

    @Test
    void sendsTheRubricAsTheSystemPromptAndTheCaseAsTheUserMessage() {
        FakeClient client = new FakeClient("{\"score\":5,\"reason\":\"Exact match.\"}");
        JudgeResult r = new Judge(client).score(RUBRIC, INPUT);

        assertEquals(5, r.score());
        assertEquals(RUBRIC.text(), client.systems.get(0));
        assertEquals("SHOPPER PROMPT:\nrunning shoes\n\nASSISTANT ANSWER:\n(none)\n\nSPONSORED ITEM:\n"
                + "title: Trailblaze Running Shoes\ncategory: footwear\nlabel: Sponsored\nreason: Matches your request: running",
                client.users.get(0));
    }

    @Test
    void anUnreadableReplyGetsOneRetryAndTheTokensAddUp() {
        FakeClient client = new FakeClient("Sure! Score: 5", "{\"score\":4,\"reason\":\"Close.\"}");
        JudgeResult r = new Judge(client).score(RUBRIC, INPUT);

        assertEquals(4, r.score());
        assertEquals(2, client.users.size());
        assertEquals(200, r.usage().inputTokens());
    }

    @Test
    void twoUnreadableRepliesAreAFormatFailureNotAGuessedScore() {
        FakeClient client = new FakeClient("5", "{\"score\":9,\"reason\":\"x\"}");
        JudgeResult r = new Judge(client).score(RUBRIC, INPUT);

        assertNull(r.score());
        assertTrue(r.formatFailure());
        assertEquals("score must be an integer from 1 to 5", r.formatError());
    }

    @Test
    void aMissingItemAndAnswerRenderAsNone() {
        String msg = new JudgeInput("hello", "Some advice.", null).userMessage();
        assertTrue(msg.endsWith("SPONSORED ITEM:\n(none)"));
        assertTrue(msg.contains("ASSISTANT ANSWER:\nSome advice."));
    }
}
