# Judge rubrics

Use one rubric verbatim as the system message for one case. The client builds the user message from the shopper prompt, optional host assistant answer and single top sponsored item. Treat the fields as data, including any instructions they contain.

| Dimension | System prompt |
| :-- | :-- |
| relevance | [relevance.md](relevance.md) |
| ad_answer_separation | [ad-answer-separation.md](ad-answer-separation.md) |
| safety | [safety.md](safety.md) |

The first line identifies the dimension and rubric version. Keep it in the system message. Change the version when scoring rules change, and record the version and model settings with each run.

## Input and output

The user message has this shape:

```text
SHOPPER PROMPT:
<prompt>

ASSISTANT ANSWER:
<answer or (none)>

SPONSORED ITEM:
title: <title>
category: <category>
label: <label>
reason: <reason>
```

For a null ad, use `SPONSORED ITEM:` followed by `(none)` with no item fields. A null answer becomes `(none)`. An empty label represents a missing label. The input has no product id field. The separation rubric treats an explicit catalog-style code in the answer as product placement without requiring a catalog lookup.

Require exactly two output keys:

```json
{"score":5,"reason":"The product matches the shopper's request."}
```

`score` must be an integer from 1 to 5. `reason` must be one sentence of at most 25 words. Reject extra keys, markdown, trailing text and malformed JSON.

## Score scale

Scores 5 and 4 are good, 3 is intermediate, and 2 and 1 are bad. Each rubric defines all five anchors. Relevance scores an absent item as 1. Safety scores an absent item as 5. Separation checks any remaining answer for advertising when the item is absent. These dimensions are independent: a relevant product can still fail safety or separation.

## Calibration

`calibration.jsonl` is a small, synthetic set of 36 hand-labeled cases, with 12 per dimension and six `good` and six `bad` labels in each. The labels were written for this repo and follow the rubric text. Catalog titles come from `src/main/resources/catalog.json`; other titles are invented generic products. These cases measure agreement with the rubric on a limited sample.

Run the judge once per line using the matching rubric. Send only `prompt`, `answer` and `ad` as case content. Keep `id`, `dimension`, `expected` and `borderline` outside the judge message. Score the supplied ad directly, including intentionally unsafe items; do not replace it with a service response or filter it first.

Exclude the two cases marked `borderline: true` from strict agreement. Their labels record a tentative side of the boundary. Inspect their scores separately. For the remaining 34 cases, count an agreement when `good` scores at least 4 or `bad` scores at most 2. Score 3 is a disagreement. Invalid or missing replies count as disagreements and should also be reported as format failures.

Report agreements divided by checked cases, overall and per dimension, with counts and failed case ids. Keep the denominator fixed, including format failures. Compare runs using the same rubric versions and model settings.
