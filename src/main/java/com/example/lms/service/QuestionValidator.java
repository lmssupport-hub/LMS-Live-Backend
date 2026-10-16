package com.example.lms.service;

import com.example.lms.dto.QuestionDto;
import com.example.lms.entity.QuestionType;
import com.example.lms.entity.ScoringType;
import com.example.lms.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single place for every M5F1 business rule. Used by the REST path AND the CSV import path,
 * so imported rows can never bypass a rule that the UI enforces.
 */
@Component
public class QuestionValidator {

    static final int MAX_QUESTION_CHARS = 500;
    static final int MAX_OPTION_CHARS = 200;
    static final int MAX_FEEDBACK_CHARS = 500;
    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 20;
    static final int MIN_SCORE = 0;
    static final int MAX_SCORE = 100;

    public void validate(QuestionDto.QuestionRequest r) {
        String text = r.getText() == null ? "" : r.getText().trim();
        if (text.isEmpty()) throw bad("Question is required.");
        if (text.length() > MAX_QUESTION_CHARS) throw bad("Question must not exceed 500 characters.");

        QuestionType type = QuestionType.from(r.getType()).orElseThrow(() ->
                bad("Question Type is invalid. Allowed: SINGLE_ANSWER, MULTIPLE_ANSWER, SHORT_TEXT."));

        if (r.getScoringType() != null && !r.getScoringType().isBlank()
                && ScoringType.from(r.getScoringType()).isEmpty()) {
            throw bad("Scoring Type is invalid. Allowed: BASIC, ADVANCED.");
        }

        checkScore(r.getCorrectScore());
        checkScore(r.getIncorrectScore());
        checkLength(r.getCorrectFeedback(), MAX_FEEDBACK_CHARS, "Feedback");
        checkLength(r.getIncorrectFeedback(), MAX_FEEDBACK_CHARS, "Feedback");
        checkLength(r.getExpectedAnswer(), MAX_QUESTION_CHARS, "Answer");

        List<QuestionDto.OptionRequest> options = r.getOptions() == null ? List.of() : r.getOptions();

        if (type == QuestionType.SHORT_TEXT) {
            if (!options.isEmpty()) throw bad("Options are not allowed for Short Text questions.");
            return;
        }

        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw bad("A question must have between " + MIN_OPTIONS + " and " + MAX_OPTIONS + " options.");
        }

        Set<String> seen = new HashSet<>();
        int correctCount = 0;
        for (QuestionDto.OptionRequest o : options) {
            if (o == null) throw bad("Option text is required.");
            String optText = o.getText() == null ? "" : o.getText().trim();
            if (optText.isEmpty()) throw bad("Option text is required.");
            if (optText.length() > MAX_OPTION_CHARS) throw bad("Option must not exceed 200 characters.");
            if (!seen.add(optText.toLowerCase(Locale.ROOT))) throw bad("Duplicate options are not allowed.");
            checkScore(o.getScore());
            if (o.isCorrect()) correctCount++;
        }

        if (type == QuestionType.SINGLE_ANSWER && correctCount != 1) {
            throw bad("A Single Answer question must have exactly one correct option.");
        }
        if (type == QuestionType.MULTIPLE_ANSWER && correctCount < 1) {
            throw bad("A Multiple Answer question must have at least one correct option.");
        }
    }

    private void checkScore(Integer score) {
        if (score != null && (score < MIN_SCORE || score > MAX_SCORE)) {
            throw bad("Score must be between 0 and 100.");
        }
    }

    private void checkLength(String value, int max, String label) {
        if (value != null && value.trim().length() > max) {
            throw bad(label + " must not exceed " + max + " characters.");
        }
    }

    private ApiException bad(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }
}