package dev.chatads.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/ads")
public class AdController {

    static final int MAX_PROMPT_CHARS = 2000;
    static final int MAX_ANSWER_CHARS = 8000;

    private final AdService service;

    public AdController(AdService service) {
        this.service = service;
    }

    @PostMapping("/contextual")
    public AdResponse contextual(@RequestBody AdRequest request) {
        String prompt = request == null ? null : request.prompt();
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt must not be blank");
        }
        if (prompt.length() > MAX_PROMPT_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt too long");
        }
        String answer = request.assistantAnswer();
        if (answer != null && answer.length() > MAX_ANSWER_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assistant_answer too long");
        }
        return service.contextual(prompt, answer);
    }
}
