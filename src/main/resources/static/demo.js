(function () {
  var SAMPLES = [
    {
      prompt: "noise cancelling headphones for my commute",
      answer: "For a daily commute, look for strong noise cancelling, comfortable ear cups and at least 20 hours of battery."
    },
    {
      prompt: "lightweight laptop for a student",
      answer: ""
    },
    {
      prompt: "where can I buy a vape",
      answer: "Age limits and local rules apply, so check what is legal where you live before ordering."
    }
  ];

  var form = document.getElementById("form");
  var promptInput = document.getElementById("prompt");
  var answerInput = document.getElementById("answer");
  var submit = document.getElementById("submit");
  var result = document.getElementById("result");
  var statusLine = document.getElementById("status");
  var answerOut = document.getElementById("answer-out");
  var adsOut = document.getElementById("ads");
  var raw = document.getElementById("raw");

  document.querySelectorAll("[data-sample]").forEach(function (button) {
    button.addEventListener("click", function () {
      var sample = SAMPLES[Number(button.dataset.sample)];
      promptInput.value = sample.prompt;
      answerInput.value = sample.answer;
      form.requestSubmit();
    });
  });

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    send();
  });

  function setStatus(text, isError) {
    statusLine.textContent = text;
    statusLine.className = isError ? "status error" : "status";
  }

  function clearOutput() {
    answerOut.textContent = "";
    adsOut.replaceChildren();
    raw.textContent = "";
  }

  function send() {
    var body = { prompt: promptInput.value };
    if (answerInput.value.trim() !== "") {
      body.assistant_answer = answerInput.value;
    }
    submit.disabled = true;
    result.hidden = false;
    result.setAttribute("aria-busy", "true");
    clearOutput();
    setStatus("Asking the service...", false);

    var started = performance.now();
    // A free instance that has been asleep can need most of a minute to start.
    var wakeNotice = setTimeout(function () {
      setStatus("Still waiting. The server was probably asleep and is starting up, which can take about a minute.", false);
    }, 4000);
    var controller = new AbortController();
    var giveUp = setTimeout(function () { controller.abort(); }, 120000);

    fetch("/v1/ads/contextual", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: controller.signal
    })
      .then(function (response) {
        return response.text().then(function (text) {
          return { ok: response.ok, status: response.status, text: text };
        });
      })
      .then(function (res) {
        var elapsed = Math.round(performance.now() - started);
        if (!res.ok) {
          raw.textContent = res.text;
          setStatus(res.status === 429
            ? "Too many requests from this address. Wait a minute and try again."
            : "The service answered with HTTP " + res.status + ".", true);
          return;
        }
        var data = JSON.parse(res.text);
        render(data);
        raw.textContent = JSON.stringify(data, null, 2);
        setStatus("Answered in " + elapsed + " ms.", false);
      })
      .catch(function () {
        setStatus("No response. The server may still be waking up, so try again in a few seconds.", true);
      })
      .finally(function () {
        clearTimeout(wakeNotice);
        clearTimeout(giveUp);
        submit.disabled = false;
        result.setAttribute("aria-busy", "false");
      });
  }

  function render(data) {
    if (data.answer_slot) {
      answerOut.className = "answer";
      answerOut.textContent = data.answer_slot;
    } else {
      answerOut.className = "answer empty";
      answerOut.textContent = "No assistant answer was sent.";
    }

    var items = data.sponsored || [];
    if (items.length === 0) {
      var empty = document.createElement("li");
      empty.className = "empty-ads";
      empty.textContent = "No ad for this prompt. Either nothing in the catalog matched, or the matches were in a banned category and the policy filter removed them.";
      adsOut.appendChild(empty);
      return;
    }
    items.forEach(function (item) {
      var li = document.createElement("li");
      li.appendChild(part("span", "label", item.label));
      li.appendChild(part("div", "ad-title", item.title));
      li.appendChild(part("div", "ad-reason", item.reason));
      li.appendChild(part("div", "ad-id", item.product_id));
      adsOut.appendChild(li);
    });
  }

  function part(tag, className, text) {
    var el = document.createElement(tag);
    el.className = className;
    el.textContent = text;
    return el;
  }
})();
