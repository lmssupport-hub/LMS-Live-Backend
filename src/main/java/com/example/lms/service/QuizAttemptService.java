package com.example.lms.service;

import com.example.lms.dto.QuestionDto;
import com.example.lms.dto.QuizAttemptDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionBankEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionOptionEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionSectionEntity;
import com.example.lms.entity.QuestionType;
import com.example.lms.entity.QuizAnswerEntity;
import com.example.lms.entity.QuizAttemptEntity;
import com.example.lms.entity.ScoringType;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.QuestionRepository;
import com.example.lms.repository.QuestionSectionRepository;
import com.example.lms.repository.QuizAnswerRepository;
import com.example.lms.repository.QuizAttemptRepository;
import com.example.lms.repository.SectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A learner attempts a Question Bank: start / resume, save answers, submit a question section, finish and get a score.
 *
 * Scoring (same fields the instructor sets in the Question Bank editor):
 *  - BASIC    : the question's correct score when the answer is right, its incorrect score when it is wrong,
 *               0 when it is not answered. Multiple answer = all correct options and nothing else.
 *               Short text = equals the expected answer (ignoring case and extra spaces).
 *  - ADVANCED : the sum of the scores of the options the learner picked.
 */
@Service
public class QuizAttemptService {

    /** How many times a learner may take the same quiz (Score page: "Attempts - 01/03"). */
    @Value("${quiz.max-attempts:3}")
    private int maxAttempts;

    private final CourseAccessService courseAccess;
    private final EnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final QuestionBankRepository bankRepository;
    private final QuestionSectionRepository questionSectionRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;

    public QuizAttemptService(CourseAccessService courseAccess,
                              EnrollmentRepository enrollmentRepository,
                              SectionRepository sectionRepository,
                              QuestionBankRepository bankRepository,
                              QuestionSectionRepository questionSectionRepository,
                              QuestionRepository questionRepository,
                              QuizAttemptRepository attemptRepository,
                              QuizAnswerRepository answerRepository) {
        this.courseAccess = courseAccess;
        this.enrollmentRepository = enrollmentRepository;
        this.sectionRepository = sectionRepository;
        this.bankRepository = bankRepository;
        this.questionSectionRepository = questionSectionRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
    }

    // ------------------------------------------------------------------
    // list the quizzes of a course section (learner side)
    // ------------------------------------------------------------------

    /**
     * The "Attempt Quiz" tabs of a course section. The instructor-side list (GET .../question-banks) needs the
     * "organize modules" permission, so a learner could not see any quiz; this one only needs read access to the
     * course (enrolled learner) and returns no questions or answers. Banks without a visible question are left out.
     */
    @Transactional(readOnly = true)
    public List<QuestionDto.BankResponse> listQuizzes(Long courseId, Long sectionId) {
        courseAccess.requireReadableCourse(courseId);
        sectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new ApiException("Section not found with id: " + sectionId, HttpStatus.NOT_FOUND));

        List<QuestionDto.BankResponse> result = new ArrayList<>();
        for (QuestionBankEntity bank : bankRepository.findBySectionIdOrderByBankOrderAscIdAsc(sectionId)) {
            long questionSections = 0;
            long questions = 0;
            for (QuestionSectionEntity qs : questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bank.getId())) {
                long visible = questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qs.getId())
                        .stream().filter(q -> !q.isHidden()).count();
                if (visible > 0) {
                    questionSections++;
                    questions += visible;
                }
            }
            if (questions == 0) continue;
            result.add(QuestionDto.BankResponse.builder()
                    .id(bank.getId())
                    .courseId(courseId)
                    .sectionId(sectionId)
                    .name(bank.getName())
                    .bankOrder(bank.getBankOrder())
                    .questionSectionCount(questionSections)
                    .totalQuestionCount(questions)
                    .createdAt(bank.getCreatedAt())
                    .updatedAt(bank.getUpdatedAt())
                    .build());
        }
        return result;
    }

    // ------------------------------------------------------------------
    // overview / start / retest
    // ------------------------------------------------------------------

    /**
     * Opens the quiz page WITHOUT starting the timer.
     * NOT_STARTED -> the "Ready to take the quiz?" page, IN_PROGRESS -> resume, SUBMITTED -> the Score page.
     */
    @Transactional(readOnly = true)
    public QuizAttemptDto.AttemptResponse overview(Long courseId, Long sectionId, Long bankId) {
        User user = requireLearner(courseId);
        QuestionBankEntity bank = requireBank(courseId, sectionId, bankId);
        QuizAttemptEntity attempt = attemptRepository.findByUserIdAndBankId(user.getId(), bankId).orElse(null);
        if (attempt == null) {
            return notStarted(courseId, sectionId, bank);
        }
        return toResponse(attempt, bank);
    }

    /** "Start Quiz": creates the attempt (the timer starts). If it already exists it is simply returned (resume / result). */
    @Transactional
    public QuizAttemptDto.AttemptResponse start(Long courseId, Long sectionId, Long bankId) {
        User user = requireLearner(courseId);
        QuestionBankEntity bank = requireBank(courseId, sectionId, bankId);

        QuizAttemptEntity attempt = attemptRepository.findByUserIdAndBankId(user.getId(), bankId).orElse(null);
        if (attempt == null) {
            attempt = new QuizAttemptEntity();
            attempt.setUserId(user.getId());
            attempt.setCourseId(courseId);
            attempt.setSectionId(sectionId);
            attempt.setBankId(bankId);
            attempt.setAttemptNumber(1);
            attempt.setStatus(QuizAttemptEntity.IN_PROGRESS);
            attempt.setStartedAt(LocalDateTime.now());
            try {
                attempt = attemptRepository.saveAndFlush(attempt);
            } catch (DataIntegrityViolationException e) {
                // two tabs started the quiz at the same moment: use the one that won
                attempt = attemptRepository.findByUserIdAndBankId(user.getId(), bankId)
                        .orElseThrow(() -> new ApiException("Could not start the quiz. Please try again.", HttpStatus.CONFLICT));
            }
        }
        return toResponse(attempt, bank);
    }

    /** "Retest": only after finishing, and only while attempts are left. Clears the answers and starts attempt n + 1. */
    @Transactional
    public QuizAttemptDto.AttemptResponse retest(Long courseId, Long sectionId, Long bankId) {
        User user = requireLearner(courseId);
        QuestionBankEntity bank = requireBank(courseId, sectionId, bankId);
        QuizAttemptEntity attempt = attemptRepository.findByUserIdAndBankId(user.getId(), bankId)
                .orElseThrow(() -> new ApiException("Quiz not started.", HttpStatus.NOT_FOUND));
        if (!QuizAttemptEntity.SUBMITTED.equals(attempt.getStatus())) {
            throw new ApiException("Finish the quiz before taking a retest.", HttpStatus.CONFLICT);
        }
        int used = attempt.getAttemptNumber() == null ? 1 : attempt.getAttemptNumber();
        if (used >= maxAttempts) {
            throw new ApiException("You have used all " + maxAttempts + " attempts for this quiz.", HttpStatus.CONFLICT);
        }
        answerRepository.deleteAll(answerRepository.findByAttemptId(attempt.getId()));
        attempt.setAttemptNumber(used + 1);
        attempt.setStatus(QuizAttemptEntity.IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setSubmittedAt(null);
        attempt.setScore(null);
        attempt.setMaxScore(null);
        attempt.setSubmittedSections(null);
        attemptRepository.save(attempt);
        return toResponse(attempt, bank);
    }

    // ------------------------------------------------------------------
    // review (after finishing)
    // ------------------------------------------------------------------

    /** Every question with the learner's answer and the right answer - only once the quiz is finished. */
    @Transactional(readOnly = true)
    public QuizAttemptDto.ReviewResponse review(Long courseId, Long sectionId, Long bankId) {
        User user = requireLearner(courseId);
        requireBank(courseId, sectionId, bankId);
        QuizAttemptEntity attempt = attemptRepository.findByUserIdAndBankId(user.getId(), bankId)
                .orElseThrow(() -> new ApiException("Quiz not started.", HttpStatus.NOT_FOUND));
        if (!QuizAttemptEntity.SUBMITTED.equals(attempt.getStatus())) {
            throw new ApiException("Finish the quiz to review the answers.", HttpStatus.CONFLICT);
        }

        Map<Long, QuizAnswerEntity> answers = answersByQuestion(attempt.getId());
        List<QuizAttemptDto.ReviewQuestion> questions = new ArrayList<>();
        int correctCount = 0;
        for (QuestionSectionEntity qs : questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bankId)) {
            for (QuestionEntity q : questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qs.getId())) {
                if (q.isHidden()) continue;
                QuizAnswerEntity a = answers.get(q.getId());
                boolean answered = a != null;
                boolean correct = answered && isCorrect(q, a);
                if (correct) correctCount++;
                Set<Long> selected = a == null ? Set.of() : new HashSet<>(parseIds(a.getOptionIds()));
                List<QuizAttemptDto.ReviewOption> options = q.getQuestionType() == QuestionType.SHORT_TEXT
                        ? List.of()
                        : q.getOptions().stream()
                            .sorted(Comparator.comparingInt(QuestionOptionEntity::getOptionOrder))
                            .map(o -> QuizAttemptDto.ReviewOption.builder()
                                    .id(o.getId()).text(o.getOptionText())
                                    .correct(o.isCorrect()).selected(selected.contains(o.getId())).build())
                            .toList();
                questions.add(QuizAttemptDto.ReviewQuestion.builder()
                        .id(q.getId())
                        .sectionName(qs.getName())
                        .text(q.getQuestionText())
                        .type(q.getQuestionType().name())
                        .correct(correct)
                        .answered(answered)
                        .options(options)
                        .givenText(a == null ? null : a.getTextAnswer())
                        .expectedAnswer(q.getQuestionType() == QuestionType.SHORT_TEXT ? q.getExpectedAnswer() : null)
                        .feedback(answered ? (correct ? q.getCorrectFeedback() : q.getIncorrectFeedback()) : null)
                        .build());
            }
        }
        return QuizAttemptDto.ReviewResponse.builder()
                .correctCount(correctCount)
                .incorrectCount(questions.size() - correctCount)
                .questions(questions)
                .build();
    }

    // ------------------------------------------------------------------
    // save one answer
    // ------------------------------------------------------------------

    @Transactional
    public void saveAnswer(Long courseId, Long sectionId, Long bankId, Long questionId,
                           QuizAttemptDto.SaveAnswerRequest request) {
        User user = requireLearner(courseId);
        requireBank(courseId, sectionId, bankId);
        QuizAttemptEntity attempt = requireOpenAttempt(user, bankId);

        QuestionEntity question = visibleQuestionsOfBank(bankId).get(questionId);
        if (question == null) {
            throw new ApiException("Question not found with id: " + questionId, HttpStatus.NOT_FOUND);
        }
        if (submittedSectionIds(attempt).contains(question.getQuestionSection().getId())) {
            throw new ApiException("This section is already submitted. Answers can't be changed.", HttpStatus.CONFLICT);
        }

        Set<Long> optionIds = new LinkedHashSet<>();
        String text = null;
        if (question.getQuestionType() == QuestionType.SHORT_TEXT) {
            text = request.getText() == null ? "" : request.getText().trim();
        } else {
            Set<Long> valid = question.getOptions().stream().map(QuestionOptionEntity::getId).collect(Collectors.toSet());
            if (request.getOptionIds() != null) {
                for (Long id : request.getOptionIds()) {
                    if (id == null || !valid.contains(id)) {
                        throw new ApiException("Invalid option selected.", HttpStatus.BAD_REQUEST);
                    }
                    optionIds.add(id);
                }
            }
            if (question.getQuestionType() == QuestionType.SINGLE_ANSWER && optionIds.size() > 1) {
                throw new ApiException("Select only one option for a single choice question.", HttpStatus.BAD_REQUEST);
            }
        }

        boolean empty = optionIds.isEmpty() && (text == null || text.isEmpty());
        QuizAnswerEntity row = answerRepository.findByAttemptIdAndQuestionId(attempt.getId(), questionId).orElse(null);
        if (empty) {
            if (row != null) answerRepository.delete(row); // answer cleared
            return;
        }
        if (row == null) {
            row = new QuizAnswerEntity();
            row.setAttemptId(attempt.getId());
            row.setQuestionId(questionId);
        }
        row.setOptionIds(optionIds.isEmpty() ? null
                : optionIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        row.setTextAnswer(text == null || text.isEmpty() ? null : text);
        row.setSavedAt(LocalDateTime.now());
        try {
            answerRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            // a parallel save created the row first: update that one
            QuizAnswerEntity existing = answerRepository.findByAttemptIdAndQuestionId(attempt.getId(), questionId)
                    .orElseThrow(() -> new ApiException("Could not save the answer. Please try again.", HttpStatus.CONFLICT));
            existing.setOptionIds(row.getOptionIds());
            existing.setTextAnswer(row.getTextAnswer());
            existing.setSavedAt(row.getSavedAt());
            answerRepository.save(existing);
        }
    }

    // ------------------------------------------------------------------
    // submit a question section / finish
    // ------------------------------------------------------------------

    /** "Submit Section": locks that question section's answers. Unanswered questions are allowed (they score 0). */
    @Transactional
    public QuizAttemptDto.AttemptResponse submitSection(Long courseId, Long sectionId, Long bankId, Long questionSectionId) {
        User user = requireLearner(courseId);
        QuestionBankEntity bank = requireBank(courseId, sectionId, bankId);
        QuizAttemptEntity attempt = requireOpenAttempt(user, bankId);

        questionSectionRepository.findByIdAndQuestionBankId(questionSectionId, bankId)
                .orElseThrow(() -> new ApiException("Section not found with id: " + questionSectionId, HttpStatus.NOT_FOUND));

        Set<Long> submitted = submittedSectionIds(attempt);
        if (submitted.add(questionSectionId)) {
            attempt.setSubmittedSections(submitted.stream().map(String::valueOf).collect(Collectors.joining(",")));
            attemptRepository.save(attempt);
        }
        return toResponse(attempt, bank);
    }

    /** "Finish": locks everything, calculates the score and returns the result. */
    @Transactional
    public QuizAttemptDto.AttemptResponse finish(Long courseId, Long sectionId, Long bankId) {
        User user = requireLearner(courseId);
        QuestionBankEntity bank = requireBank(courseId, sectionId, bankId);
        QuizAttemptEntity attempt = attemptRepository.findByUserIdAndBankId(user.getId(), bankId)
                .orElseThrow(() -> new ApiException("Quiz not started.", HttpStatus.NOT_FOUND));
        if (QuizAttemptEntity.SUBMITTED.equals(attempt.getStatus())) {
            return toResponse(attempt, bank); // already finished: same result again
        }

        Map<Long, QuizAnswerEntity> answers = answersByQuestion(attempt.getId());
        int score = 0;
        int max = 0;
        for (QuestionEntity q : visibleQuestionsOfBank(bankId).values()) {
            max += maxScore(q);
            score += grade(q, answers.get(q.getId()));
        }
        attempt.setScore(Math.max(0, score));
        attempt.setMaxScore(max);
        attempt.setStatus(QuizAttemptEntity.SUBMITTED);
        attempt.setSubmittedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
        return toResponse(attempt, bank);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Learners only: enrolled in a published course of their organization. Admins / instructors manage quizzes, they don't attempt them. */
    private User requireLearner(Long courseId) {
        User user = courseAccess.currentUser();
        courseAccess.requireReadableCourse(courseId);
        if (courseAccess.isManager(user)) {
            throw new ApiException("Only learners can attempt a quiz.", HttpStatus.FORBIDDEN);
        }
        if (!enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId())) {
            throw new ApiException("Please enroll in this course to attempt the quiz.", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private QuestionBankEntity requireBank(Long courseId, Long sectionId, Long bankId) {
        return bankRepository.findByIdAndSectionIdAndSectionCourseId(bankId, sectionId, courseId)
                .orElseThrow(() -> new ApiException("Question bank not found with id: " + bankId, HttpStatus.NOT_FOUND));
    }

    private QuizAttemptEntity requireOpenAttempt(User user, Long bankId) {
        QuizAttemptEntity attempt = attemptRepository.findByUserIdAndBankId(user.getId(), bankId)
                .orElseThrow(() -> new ApiException("Quiz not started.", HttpStatus.NOT_FOUND));
        if (QuizAttemptEntity.SUBMITTED.equals(attempt.getStatus())) {
            throw new ApiException("This quiz is already submitted.", HttpStatus.CONFLICT);
        }
        return attempt;
    }

    private Set<Long> submittedSectionIds(QuizAttemptEntity attempt) {
        Set<Long> ids = new HashSet<>();
        parseIds(attempt.getSubmittedSections()).forEach(ids::add);
        return ids;
    }

    private static List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) return ids;
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip a damaged value
            }
        }
        return ids;
    }

    private Map<Long, QuizAnswerEntity> answersByQuestion(Long attemptId) {
        Map<Long, QuizAnswerEntity> map = new HashMap<>();
        for (QuizAnswerEntity a : answerRepository.findByAttemptId(attemptId)) {
            map.put(a.getQuestionId(), a);
        }
        return map;
    }

    /** question id -> question, for the visible (not hidden) questions of the whole bank. */
    private Map<Long, QuestionEntity> visibleQuestionsOfBank(Long bankId) {
        Map<Long, QuestionEntity> map = new java.util.LinkedHashMap<>();
        for (QuestionSectionEntity qs : questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bankId)) {
            for (QuestionEntity q : questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qs.getId())) {
                if (!q.isHidden()) map.put(q.getId(), q);
            }
        }
        return map;
    }

    /** Response for a learner who has not pressed "Start Quiz" yet (no questions are sent). */
    private QuizAttemptDto.AttemptResponse notStarted(Long courseId, Long sectionId, QuestionBankEntity bank) {
        CourseEntity course = courseAccess.requireReadableCourse(courseId);
        return QuizAttemptDto.AttemptResponse.builder()
                .bankName(bank.getName())
                .courseName(course.getName())
                .thumbnailUrl(course.getThumbnailUrl())
                .status("NOT_STARTED")
                .attemptNumber(0)
                .maxAttempts(maxAttempts)
                .nextSectionId(nextSectionId(courseId, sectionId))
                .elapsedSeconds(0)
                .sections(List.of())
                .build();
    }

    private Long nextSectionId(Long courseId, Long sectionId) {
        List<com.example.lms.entity.SectionEntity> all = sectionRepository.findByCourseIdOrderBySectionOrderAsc(courseId);
        for (int i = 0; i < all.size() - 1; i++) {
            if (all.get(i).getId().equals(sectionId)) return all.get(i + 1).getId();
        }
        return null;
    }

    private QuizAttemptDto.AttemptResponse toResponse(QuizAttemptEntity attempt, QuestionBankEntity bank) {
        CourseEntity course = courseAccess.requireReadableCourse(attempt.getCourseId());
        Map<Long, QuizAnswerEntity> answers = answersByQuestion(attempt.getId());
        Set<Long> submitted = submittedSectionIds(attempt);
        boolean finished = QuizAttemptEntity.SUBMITTED.equals(attempt.getStatus());

        List<QuizAttemptDto.SectionView> sections = new ArrayList<>();
        for (QuestionSectionEntity qs : questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bank.getId())) {
            List<QuizAttemptDto.QuestionView> questions = new ArrayList<>();
            for (QuestionEntity q : questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qs.getId())) {
                if (q.isHidden()) continue;
                List<QuizAttemptDto.OptionView> options = q.getQuestionType() == QuestionType.SHORT_TEXT
                        ? List.of()
                        : q.getOptions().stream()
                            .sorted(Comparator.comparingInt(QuestionOptionEntity::getOptionOrder))
                            .map(o -> QuizAttemptDto.OptionView.builder().id(o.getId()).text(o.getOptionText()).build())
                            .toList();
                QuizAnswerEntity a = answers.get(q.getId());
                questions.add(QuizAttemptDto.QuestionView.builder()
                        .id(q.getId())
                        .text(q.getQuestionText())
                        .type(q.getQuestionType().name())
                        .options(options)
                        .answer(QuizAttemptDto.AnswerView.builder()
                                .optionIds(a == null ? List.of() : parseIds(a.getOptionIds()))
                                .text(a == null ? null : a.getTextAnswer())
                                .build())
                        .build());
            }
            if (questions.isEmpty()) continue; // a section with nothing to answer is not shown
            sections.add(QuizAttemptDto.SectionView.builder()
                    .id(qs.getId())
                    .name(qs.getName())
                    .submitted(finished || submitted.contains(qs.getId()))
                    .questions(questions)
                    .build());
        }

        LocalDateTime end = finished && attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : LocalDateTime.now();
        return QuizAttemptDto.AttemptResponse.builder()
                .attemptId(attempt.getId())
                .bankName(bank.getName())
                .courseName(course.getName())
                .thumbnailUrl(course.getThumbnailUrl())
                .status(attempt.getStatus())
                .attemptNumber(attempt.getAttemptNumber() == null ? 1 : attempt.getAttemptNumber())
                .maxAttempts(maxAttempts)
                .nextSectionId(nextSectionId(attempt.getCourseId(), attempt.getSectionId()))
                .submittedAt(finished ? attempt.getSubmittedAt() : null)
                .elapsedSeconds(Math.max(0, Duration.between(attempt.getStartedAt(), end).getSeconds()))
                .score(finished ? attempt.getScore() : null)
                .maxScore(finished ? attempt.getMaxScore() : null)
                .sections(sections)
                .build();
    }

    // ---------------- scoring ----------------

    private int maxScore(QuestionEntity q) {
        boolean advancedChoice = q.getScoringType() == ScoringType.ADVANCED && q.getQuestionType() != QuestionType.SHORT_TEXT;
        if (!advancedChoice) return Math.max(0, q.getCorrectScore());
        List<Integer> positives = q.getOptions().stream().map(QuestionOptionEntity::getScore).filter(s -> s > 0).toList();
        return q.getQuestionType() == QuestionType.SINGLE_ANSWER
                ? positives.stream().mapToInt(Integer::intValue).max().orElse(0)   // only one option can be picked
                : positives.stream().mapToInt(Integer::intValue).sum();
    }

    /** True only when the answer is fully right (used for the Correct / Incorrect Answer lists). */
    private boolean isCorrect(QuestionEntity q, QuizAnswerEntity answer) {
        if (q.getQuestionType() == QuestionType.SHORT_TEXT) {
            String given = normalize(answer.getTextAnswer());
            String expected = normalize(q.getExpectedAnswer());
            return !given.isEmpty() && !expected.isEmpty() && given.equals(expected);
        }
        Set<Long> selected = new HashSet<>(parseIds(answer.getOptionIds()));
        if (selected.isEmpty()) return false;
        if (q.getScoringType() == ScoringType.ADVANCED) {
            int max = maxScore(q);
            return max > 0 && grade(q, answer) >= max;
        }
        Set<Long> correct = q.getOptions().stream().filter(QuestionOptionEntity::isCorrect)
                .map(QuestionOptionEntity::getId).collect(Collectors.toSet());
        return selected.equals(correct);
    }

    private int grade(QuestionEntity q, QuizAnswerEntity answer) {
        if (answer == null) return 0; // not answered
        if (q.getQuestionType() == QuestionType.SHORT_TEXT) {
            String given = normalize(answer.getTextAnswer());
            String expected = normalize(q.getExpectedAnswer());
            if (given.isEmpty() || expected.isEmpty()) return 0; // nothing to compare with
            return given.equals(expected) ? q.getCorrectScore() : q.getIncorrectScore();
        }
        Set<Long> selected = new HashSet<>(parseIds(answer.getOptionIds()));
        if (selected.isEmpty()) return 0;
        if (q.getScoringType() == ScoringType.ADVANCED) {
            return q.getOptions().stream().filter(o -> selected.contains(o.getId())).mapToInt(QuestionOptionEntity::getScore).sum();
        }
        Set<Long> correct = q.getOptions().stream().filter(QuestionOptionEntity::isCorrect)
                .map(QuestionOptionEntity::getId).collect(Collectors.toSet());
        return selected.equals(correct) ? q.getCorrectScore() : q.getIncorrectScore();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}