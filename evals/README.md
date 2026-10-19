# Evals

`prompts.jsonl` is a synthetic set of 30 generic shopping prompts. Each line:

```json
{"id":"e01","prompt":"I need new running shoes for my first marathon","expected_categories":["footwear"]}
```

- `expected_categories`: the category the top sponsored item must belong to.
  An empty list means no ad should be shown (banned-category prompts and off-topic prompts).
- The set is written against the synthetic catalog in `src/main/resources/catalog.json`.
  It checks that the pipeline behaves as designed. It is not a measure of real-world quality.

## Run

```bash
./mvnw spring-boot:run &      # start the service
./mvnw -q exec:java -Dexec.mainClass=dev.chatads.eval.EvalRunner
```

Options: `--base-url`, `--dataset`, `--report`, `--mode deterministic|judge`.
The runner prints one line per prompt, writes `target/eval-report.json` and exits non-zero if any check fails.
The same set also runs in-process in `EvalSetTest`, so `./mvnw verify` catches dataset drift.

## Checks per response

| Check | What it verifies |
| :-- | :-- |
| `schema_valid` | Exactly `answer_slot` and `sponsored`; every item has non-blank `product_id`, `title`, `label`, `reason` |
| `labeled_sponsored` | Every item has the label `Sponsored` |
| `ad_not_in_answer` | `answer_slot` text never contains an ad's title, reason or product id |
| `no_banned_category` | No item is in `src/main/resources/policy/banned-categories.txt` |
| `expected_category` | Top item matches `expected_categories` (or nothing is shown when empty) |

## LLM-as-judge (stub)

`--mode judge` reads the key from the `LLM_JUDGE_API_KEY` environment variable.
With no key it prints `SKIPPED` and exits 0. With a key it currently prints that the judge is not
implemented and scores nothing. The real judge (relevance, ad-answer separation, safety) is Phase 3.
