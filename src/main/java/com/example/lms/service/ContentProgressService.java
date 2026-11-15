package com.example.lms.service;

import com.example.lms.dto.SectionProgressDto;
import com.example.lms.entity.ContentProgressEntity;
import com.example.lms.entity.MultimediaEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionBankEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionSectionEntity;
import com.example.lms.repository.ContentProgressRepository;
import com.example.lms.repository.EbookRepository;
import com.example.lms.repository.MultimediaRepository;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.QuestionRepository;
import com.example.lms.repository.QuestionSectionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item level progress of a learner: every multimedia file (later e-book) of a course section and the section's quiz.
 *
 * Rules:
 *  - a content item is COMPLETED only at 100 % (the learner really went through it - the browser reports that);
 *  - the quiz of a section opens only when ALL its content items are completed (see remainingContent);
 *  - the quiz item is 100 % only after the quiz is SUBMITTED (recorded by QuizAttemptService), never when it is merely opened.
 */
@Service
public class ContentProgressService {

    public static final String EBOOK = "EBOOK";
    public static final String MULTIMEDIA = "MULTIMEDIA";
    public static final String QUIZ = "QUIZ";

    private final EbookRepository ebookRepository;
    private final MultimediaRepository multimediaRepository;
    private final QuestionBankRepository bankRepository;
    private final QuestionSectionRepository questionSectionRepository;
    private final QuestionRepository questionRepository;
    private final ContentProgressRepository progressRepository;

    public ContentProgressService(EbookRepository ebookRepository,
                                  MultimediaRepository multimediaRepository,
                                  QuestionBankRepository bankRepository,
                                  QuestionSectionRepository questionSectionRepository,
                                  QuestionRepository questionRepository,
                                  ContentProgressRepository progressRepository) {
        this.ebookRepository = ebookRepository;
        this.multimediaRepository = multimediaRepository;
        this.bankRepository = bankRepository;
        this.questionSectionRepository = questionSectionRepository;
        this.questionRepository = questionRepository;
        this.progressRepository = progressRepository;
    }

    private static String key(String type, Long itemId) {
        return type + ":" + itemId;
    }

    /** "TYPE:id" -> percent, for one learner in one course. */
    @Transactional(readOnly = true)
    public Map<String, Integer> storedPercents(Long userId, Long courseId) {
        Map<String, Integer> map = new HashMap<>();
        for (ContentProgressEntity p : progressRepository.findByUserIdAndCourseId(userId, courseId)) {
            map.put(key(p.getItemType(), p.getItemId()), p.getProgressPercent() == null ? 0 : p.getProgressPercent());
        }
        return map;
    }

    /** The learning items of a section (multimedia files, then quizzes that have questions) with the learner's percent. */
    @Transactional(readOnly = true)
    public List<SectionProgressDto.ItemView> itemsOfSection(Long sectionId, Map<String, Integer> stored) {
        List<SectionProgressDto.ItemView> items = new ArrayList<>();
        for (EbookRepository.EbookSummary e : ebookRepository.findSummariesBySectionIdOrderByCreatedAtAsc(sectionId)) {
            items.add(SectionProgressDto.ItemView.builder()
                    .sectionId(sectionId).itemType(EBOOK).itemId(e.getId()).name(e.getName())
                    .percent(stored.getOrDefault(key(EBOOK, e.getId()), 0)).build());
        }
        for (MultimediaEntity m : multimediaRepository.findBySectionIdOrderByCreatedAtAsc(sectionId)) {
            items.add(SectionProgressDto.ItemView.builder()
                    .sectionId(sectionId).itemType(MULTIMEDIA).itemId(m.getId()).name(m.getName())
                    .percent(stored.getOrDefault(key(MULTIMEDIA, m.getId()), 0)).build());
        }
        for (QuestionBankEntity bank : bankRepository.findBySectionIdOrderByBankOrderAscIdAsc(sectionId)) {
            if (!hasVisibleQuestion(bank.getId())) continue; // an empty quiz is not shown to learners, so it can't block anything
            items.add(SectionProgressDto.ItemView.builder()
                    .sectionId(sectionId).itemType(QUIZ).itemId(bank.getId()).name(bank.getName())
                    .percent(stored.getOrDefault(key(QUIZ, bank.getId()), 0)).build());
        }
        return items;
    }

    /** Names of the content items (not quizzes) of the section that the learner has not completed yet. Empty = quiz may open. */
    @Transactional(readOnly = true)
    public List<String> remainingContent(Long userId, Long courseId, Long sectionId) {
        Map<String, Integer> stored = storedPercents(userId, courseId);
        List<String> remaining = new ArrayList<>();
        for (EbookRepository.EbookSummary e : ebookRepository.findSummariesBySectionIdOrderByCreatedAtAsc(sectionId)) {
            if (stored.getOrDefault(key(EBOOK, e.getId()), 0) < 100) remaining.add(e.getName());
        }
        for (MultimediaEntity m : multimediaRepository.findBySectionIdOrderByCreatedAtAsc(sectionId)) {
            if (stored.getOrDefault(key(MULTIMEDIA, m.getId()), 0) < 100) remaining.add(m.getName());
        }
        return remaining;
    }

    /** Raises the item's percent (never lowers it). */
    @Transactional
    public void record(Long userId, Long courseId, Long sectionId, String type, Long itemId, int percent) {
        int value = Math.max(0, Math.min(100, percent));
        LocalDateTime now = LocalDateTime.now();
        ContentProgressEntity row = progressRepository.findByUserIdAndItemTypeAndItemId(userId, type, itemId).orElse(null);
        if (row == null) {
            row = new ContentProgressEntity();
            row.setUserId(userId);
            row.setCourseId(courseId);
            row.setSectionId(sectionId);
            row.setItemType(type);
            row.setItemId(itemId);
            row.setProgressPercent(value);
            row.setUpdatedAt(now);
            if (value >= 100) row.setCompletedAt(now);
            try {
                progressRepository.saveAndFlush(row);
                return;
            } catch (DataIntegrityViolationException e) {
                // two requests raced: raise the row that won
                row = progressRepository.findByUserIdAndItemTypeAndItemId(userId, type, itemId).orElse(null);
                if (row == null) return;
            }
        }
        int current = row.getProgressPercent() == null ? 0 : row.getProgressPercent();
        if (value > current) {
            row.setProgressPercent(value);
            row.setUpdatedAt(now);
            if (value >= 100 && row.getCompletedAt() == null) row.setCompletedAt(now);
            progressRepository.save(row);
        }
    }

    private boolean hasVisibleQuestion(Long bankId) {
        for (QuestionSectionEntity qs : questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bankId)) {
            if (questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qs.getId())
                    .stream().anyMatch(q -> !q.isHidden())) {
                return true;
            }
        }
        return false;
    }
}