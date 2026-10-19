# Evals

`prompts.jsonl` contains 65 synthetic shopping prompts for the catalog in
`src/main/resources/catalog.json`. Cases e01 through e30 are unchanged. Cases e31
through e65 cover retrieval edge cases. These checks measure behavior against a
fixed catalog, so passing them says little about relevance on real traffic.

Each line is a JSON object:

```json
{"id":"e01","prompt":"I need new running shoes for my first marathon","expected_categories":["footwear"]}
```

| Field | Meaning |
| :-- | :-- |
| `id` | Unique case identifier |
| `prompt` | Shopper request sent to the service |
| `expected_categories` | Categories allowed for the top sponsored item. An empty list requires no ad. |
| `assistant_answer` | Optional host answer sent with the prompt and returned in `answer_slot`. Ten cases supply two sentences of neutral shopping advice for the ad separation checks. |
| `tags` | Optional list of case labels. Missing tags default to an empty list. `hybrid_only` changes the keyword test's expected outcome. |

Multi-intent cases list both requested categories. The category check accepts
either for the top item; it does not require an ad for each need or check the
categories of lower-ranked items. Banned-category checks apply to every item.

## Tags and coverage

These counts describe the set exercised by `EvalSetTest` and `HybridEvalSetTest`.
Tags overlap, so their counts do not add up to the total. The original 30 cases
have no tags.

| Tag | Cases | Meaning |
| :-- | --: | :-- |
| `multi_intent` | 8 | Two shopping needs from different categories in one prompt |
| `misspelling` | 6 | Typos in catalog terms, including shoes, laptop, stretching and sunscreen |
| `short_prompt` | 15 | One or two words, including typos and a request outside the catalog |
| `indirect_banned` | 7 | Requests pointing at tobacco, weapons, alcohol or gambling without naming those categories |
| `no_match` | 5 | Off-topic requests or an item outside the catalog, all expecting no ad |
| `hybrid_only` | 7 | Cases that fail on the keyword baseline and pass on hybrid retrieval |
| `paraphrase` | 0 | Alternate wording confirmed to pass. None is included in this set. |

The hashing embedder compares words and character trigrams. It has no synonym
knowledge. The seven `hybrid_only` cases are six misspellings and `coffee`, which
has too little exact keyword overlap to pass the baseline's score threshold.

Indirect banned cases require an empty sponsored list after retrieval and policy
filtering. Some may retrieve nothing in the first place. A passing response alone
does not prove that a policy rejection occurred.

## Run the tests

Use Java 21 and run the two test commands separately:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH
export MAVEN_OPTS=-Xmx1g
./mvnw -B -q -Dtest=EvalSetTest test
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw -B -q -Dtest=HybridEvalSetTest test
```

`EvalSetTest` uses the in-memory keyword retriever. Its two tests require the 58
cases without the `hybrid_only` tag to pass and the seven tagged ones to fail. A
`hybrid_only` case that starts passing needs its tag reviewed.

`HybridEvalSetTest` runs all 65 cases through the controller with a real Postgres
full-text and vector index and the deterministic hashing embedder. It requires
every case to pass. Docker must be available; otherwise this test is skipped.

For an already running service backed by hybrid retrieval, run the HTTP gate:

```bash
./mvnw -B -q exec:java -Dexec.mainClass=dev.chatads.eval.EvalRunner
```

Options: `--base-url`, `--dataset`, `--report`, `--mode deterministic|judge`.
The runner prints each result, writes `target/eval-report.json` and exits non-zero
if a case fails. It sends `assistant_answer` when present and checks every case
regardless of tags. Against a keyword-only service, the seven `hybrid_only` cases
therefore fail the HTTP gate.

## Checks per response

| Check | What it verifies |
| :-- | :-- |
| `schema_valid` | Exactly `answer_slot` and `sponsored`; every item has non-blank `product_id`, `title`, `label`, `reason` |
| `labeled_sponsored` | Every item has the label `Sponsored` |
| `ad_not_in_answer` | `answer_slot` text contains none of an ad's title, reason or product id |
| `no_banned_category` | No item is in `src/main/resources/policy/banned-categories.txt` |
| `expected_category` | Top item matches `expected_categories`, or no item is shown when the list is empty |

## Judge stub

`--mode judge` reads `LLM_JUDGE_API_KEY`. With no key it prints `SKIPPED` and exits
0. With a key it reports that judging is unimplemented and scores nothing.
