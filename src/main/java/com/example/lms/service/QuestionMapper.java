package com.example.lms.service;

import com.example.lms.dto.QuestionDto;
import com.example.lms.entity.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Entity <-> DTO mapping only. Assumes the request already passed {@link QuestionValidator}. */
@Component
public class QuestionMapper {

    public QuestionDto.QuestionResponse toResponse(QuestionEntity q) {
        List<QuestionDto.OptionResponse> options = q.getOptions().stream()
                .map(o -> QuestionDto.OptionResponse.builder()
                        .id(o.getId())
                        .text(o.getOptionText())
                        .correct(o.isCorrect())
                        .score(o.getScore())
                        .order(o.getOptionOrder())
                        .build())
                .toList();

        return QuestionDto.QuestionResponse.builder()
                .id(q.getId())
                .sectionId(q.getSection().getId())
                .text(q.getQuestionText())
                .type(q.getQuestionType())
                .scoringType(q.getScoringType())
                .correctScore(q.getCorrectScore())
                .incorrectScore(q.getIncorrectScore())
                .correctFeedback(q.getCorrectFeedback())
                .incorrectFeedback(q.getIncorrectFeedback())
                .expectedAnswer(q.getExpectedAnswer())
                .hidden(q.isHidden())
                .questionOrder(q.getQuestionOrder())
                .version(q.getVersion())
                .options(options)
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    public void apply(QuestionEntity q, QuestionDto.QuestionRequest r) {
        QuestionType type = QuestionType.from(r.getType())
                .orElseThrow(() -> new IllegalStateException("Request was not validated."));
        ScoringType scoring = type == QuestionType.SHORT_TEXT
                ? ScoringType.BASIC
                : ScoringType.from(r.getScoringType()).orElse(ScoringType.BASIC);

        q.setQuestionText(r.getText().trim());
        q.setQuestionType(type);
        q.setScoringType(scoring);
        q.setCorrectScore(zeroIfNull(r.getCorrectScore()));
        q.setIncorrectScore(zeroIfNull(r.getIncorrectScore()));
        q.setCorrectFeedback(blankToNull(r.getCorrectFeedback()));
        q.setIncorrectFeedback(blankToNull(r.getIncorrectFeedback()));
        q.setExpectedAnswer(type == QuestionType.SHORT_TEXT ? blankToNull(r.getExpectedAnswer()) : null);

        List<QuestionOptionEntity> options = new ArrayList<>();
        if (type != QuestionType.SHORT_TEXT) {
            int order = 1;
            for (QuestionDto.OptionRequest o : r.getOptions()) {
                QuestionOptionEntity opt = new QuestionOptionEntity();
                opt.setQuestion(q);
                opt.setOptionText(o.getText().trim());
                opt.setCorrect(o.isCorrect());
                opt.setScore(scoring == ScoringType.ADVANCED ? zeroIfNull(o.getScore()) : 0);
                opt.setOptionOrder(order++);
                options.add(opt);
            }
        }
        q.replaceOptions(options);
    }

    /** Deep copy (new identity, version reset) used by duplicate / copy / add-existing / move. */
    public QuestionEntity copyOf(QuestionEntity src, SectionEntity target, int order) {
        QuestionEntity c = new QuestionEntity();
        c.setSection(target);
        c.setQuestionText(src.getQuestionText());
        c.setQuestionType(src.getQuestionType());
        c.setScoringType(src.getScoringType());
        c.setCorrectScore(src.getCorrectScore());
        c.setIncorrectScore(src.getIncorrectScore());
        c.setCorrectFeedback(src.getCorrectFeedback());
        c.setIncorrectFeedback(src.getIncorrectFeedback());
        c.setExpectedAnswer(src.getExpectedAnswer());
        c.setHidden(src.isHidden());
        c.setQuestionOrder(order);

        List<QuestionOptionEntity> options = new ArrayList<>();
        for (QuestionOptionEntity so : src.getOptions()) {
            QuestionOptionEntity o = new QuestionOptionEntity();
            o.setQuestion(c);
            o.setOptionText(so.getOptionText());
            o.setCorrect(so.isCorrect());
            o.setScore(so.getScore());
            o.setOptionOrder(so.getOptionOrder());
            options.add(o);
        }
        c.replaceOptions(options);
        return c;
    }

    private int zeroIfNull(Integer v) {
        return v == null ? 0 : v;
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}