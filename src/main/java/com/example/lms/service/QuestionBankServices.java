package com.example.lms.service;

import com.example.lms.dto.QuestionDto;
import com.example.lms.entity.*;
import com.example.lms.entity.QuestionBankEntities.QuestionBankEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionOptionEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionSectionEntity;
import com.example.lms.entity.QuestionType;
import com.example.lms.entity.SectionEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.QuestionRepository;
import com.example.lms.repository.QuestionSectionRepository;
import com.example.lms.repository.SectionRepository;
import com.example.lms.util.CsvUtil;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** All Question Bank service classes in one file (static nested classes). */
public class QuestionBankServices {

    private QuestionBankServices() { }

    /**
     * Question Bank = the activity added inside an organize section.
     * Course -> Section -> Question Bank -> Question Section -> Questions
     */
    @Service
    @Transactional(readOnly = true)
    public static class QuestionBankService {

        private static final int MIN_NAME_CHARS = 3;
        private static final int MAX_NAME_CHARS = 100;

        private final QuestionBankRepository bankRepository;
        private final SectionRepository sectionRepository;
        private final QuestionSectionRepository questionSectionRepository;
        private final QuestionRepository questionRepository;

        public QuestionBankService(QuestionBankRepository bankRepository,
                                   SectionRepository sectionRepository,
                                   QuestionSectionRepository questionSectionRepository,
                                   QuestionRepository questionRepository) {
            this.bankRepository = bankRepository;
            this.sectionRepository = sectionRepository;
            this.questionSectionRepository = questionSectionRepository;
            this.questionRepository = questionRepository;
        }

        @Transactional
        public QuestionDto.BankResponse createBank(Long courseId, Long sectionId, QuestionDto.BankRequest request) {
            SectionEntity section = getOrganizeSectionOrThrow(courseId, sectionId);
            String name = validateName(request.getName());

            if (bankRepository.existsBySectionIdAndNameIgnoreCase(sectionId, name)) {
                throw new ApiException("A question bank with the same name already exists in this section.", HttpStatus.CONFLICT);
            }

            Integer max = bankRepository.findMaxOrderBySectionId(sectionId);
            QuestionBankEntity bank = new QuestionBankEntity();
            bank.setSection(section);
            bank.setName(name);
            bank.setBankOrder((max == null ? 0 : max) + 1);
            return toResponse(bankRepository.save(bank), courseId);
        }

        public List<QuestionDto.BankResponse> getBanks(Long courseId, Long sectionId) {
            getOrganizeSectionOrThrow(courseId, sectionId);
            List<QuestionDto.BankResponse> out = new ArrayList<>();
            for (QuestionBankEntity b : bankRepository.findBySectionIdOrderByBankOrderAscIdAsc(sectionId)) {
                out.add(toResponse(b, courseId));
            }
            return out;
        }

        public QuestionDto.BankResponse getBank(Long courseId, Long sectionId, Long bankId) {
            return toResponse(getBankOrThrow(courseId, sectionId, bankId), courseId);
        }

        @Transactional
        public QuestionDto.BankResponse updateBank(Long courseId, Long sectionId, Long bankId,
                                                       QuestionDto.BankRequest request) {
            QuestionBankEntity bank = getBankOrThrow(courseId, sectionId, bankId);
            String name = validateName(request.getName());
            if (bankRepository.existsBySectionIdAndNameIgnoreCaseAndIdNot(sectionId, name, bankId)) {
                throw new ApiException("A question bank with the same name already exists in this section.", HttpStatus.CONFLICT);
            }
            bank.setName(name);
            return toResponse(bankRepository.save(bank), courseId);
        }

        /** DB-level ON DELETE CASCADE removes the bank's question sections, questions and options. */
        @Transactional
        public void deleteBank(Long courseId, Long sectionId, Long bankId) {
            QuestionBankEntity bank = getBankOrThrow(courseId, sectionId, bankId);
            bankRepository.delete(bank);
        }

        // ---------- shared helpers (also used by QuestionSectionService) ----------

        /** Verifies the whole chain: course -> organize section -> bank. 404 if any link is wrong. */
        public QuestionBankEntity getBankOrThrow(Long courseId, Long sectionId, Long bankId) {
            return bankRepository.findByIdAndSectionIdAndSectionCourseId(bankId, sectionId, courseId)
                    .orElseThrow(() -> new ApiException(
                            "Question bank not found with id: " + bankId + " in section: " + sectionId, HttpStatus.NOT_FOUND));
        }

        private SectionEntity getOrganizeSectionOrThrow(Long courseId, Long sectionId) {
            return sectionRepository.findByIdAndCourseId(sectionId, courseId)
                    .orElseThrow(() -> new ApiException(
                            "Section not found with id: " + sectionId + " in course: " + courseId, HttpStatus.NOT_FOUND));
        }

        private String validateName(String raw) {
            String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (name.isEmpty()) throw new ApiException("Question Bank Name is required.", HttpStatus.BAD_REQUEST);
            if (name.length() < MIN_NAME_CHARS || name.length() > MAX_NAME_CHARS) {
                throw new ApiException("Question Bank Name must be between 3 and 100 characters.", HttpStatus.BAD_REQUEST);
            }
            return name;
        }

        private QuestionDto.BankResponse toResponse(QuestionBankEntity b, Long courseId) {
            long sections = questionSectionRepository.countByQuestionBankId(b.getId());
            long questions = questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(b.getId())
                    .stream().mapToLong(s -> questionRepository.countByQuestionSectionId(s.getId())).sum();
            return QuestionDto.BankResponse.builder()
                    .id(b.getId())
                    .courseId(courseId)
                    .sectionId(b.getSection().getId())
                    .name(b.getName())
                    .bankOrder(b.getBankOrder())
                    .questionSectionCount(sections)
                    .totalQuestionCount(questions)
                    .createdAt(b.getCreatedAt())
                    .updatedAt(b.getUpdatedAt())
                    .build();
        }
    }

    /**
     * Question Sections inside ONE Question Bank, e.g.
     *   Java Question Bank -> Section 1 (10 questions), Section 2 (5 questions).
     *
     * Each section also carries the "Section points" panel: score (0..100) + feedback (max 500 chars).
     */
    @Service
    @Transactional(readOnly = true)
    public static class QuestionSectionService {

        private static final int MAX_SECTIONS_PER_BANK = 100;
        private static final int MIN_SCORE = 0;
        private static final int MAX_SCORE = 100;
        private static final int MAX_FEEDBACK_CHARS = 500;

        private final QuestionSectionRepository questionSectionRepository;
        private final QuestionRepository questionRepository;
        private final QuestionBankService bankService;

        public QuestionSectionService(QuestionSectionRepository questionSectionRepository,
                                      QuestionRepository questionRepository,
                                      QuestionBankService bankService) {
            this.questionSectionRepository = questionSectionRepository;
            this.questionRepository = questionRepository;
            this.bankService = bankService;
        }

        @Transactional
        public QuestionDto.QuestionSectionResponse create(Long courseId, Long sectionId, Long bankId,
                                                                 QuestionDto.QuestionSectionRequest request) {
            QuestionBankEntity bank = bankService.getBankOrThrow(courseId, sectionId, bankId);
            String name = validateName(request.getName());
            validatePoints(request.getScore(), request.getFeedback());

            if (questionSectionRepository.countByQuestionBankId(bankId) >= MAX_SECTIONS_PER_BANK) {
                throw new ApiException("A question bank can contain at most " + MAX_SECTIONS_PER_BANK + " sections.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (questionSectionRepository.existsByQuestionBankIdAndNameIgnoreCase(bankId, name)) {
                throw new ApiException("A section with the same name already exists in this question bank.", HttpStatus.CONFLICT);
            }

            Integer max = questionSectionRepository.findMaxOrderByBankId(bankId);
            QuestionSectionEntity s = new QuestionSectionEntity();
            s.setQuestionBank(bank);
            s.setName(name);
            s.setScore(request.getScore());
            s.setFeedback(blankToNull(request.getFeedback()));
            s.setSectionOrder((max == null ? 0 : max) + 1);
            return toResponse(questionSectionRepository.save(s));
        }

        public List<QuestionDto.QuestionSectionResponse> getAll(Long courseId, Long sectionId, Long bankId) {
            bankService.getBankOrThrow(courseId, sectionId, bankId);
            List<QuestionDto.QuestionSectionResponse> out = new ArrayList<>();
            for (QuestionSectionEntity s : questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bankId)) {
                out.add(toResponse(s));
            }
            return out;
        }

        public QuestionDto.QuestionSectionResponse getOne(Long courseId, Long sectionId, Long bankId, Long questionSectionId) {
            return toResponse(getEntityOrThrow(courseId, sectionId, bankId, questionSectionId));
        }

        /** PUT = full replace of name + section points (a null score / blank feedback clears the value). */
        @Transactional
        public QuestionDto.QuestionSectionResponse update(Long courseId, Long sectionId, Long bankId, Long questionSectionId,
                                                                 QuestionDto.QuestionSectionRequest request) {
            QuestionSectionEntity s = getEntityOrThrow(courseId, sectionId, bankId, questionSectionId);
            String name = validateName(request.getName());
            validatePoints(request.getScore(), request.getFeedback());
            if (questionSectionRepository.existsByQuestionBankIdAndNameIgnoreCaseAndIdNot(bankId, name, questionSectionId)) {
                throw new ApiException("A section with the same name already exists in this question bank.", HttpStatus.CONFLICT);
            }
            s.setName(name);
            s.setScore(request.getScore());
            s.setFeedback(blankToNull(request.getFeedback()));
            return toResponse(questionSectionRepository.save(s));
        }

        /** DB-level ON DELETE CASCADE removes this section's questions and options. Remaining sections are renumbered. */
        @Transactional
        public void delete(Long courseId, Long sectionId, Long bankId, Long questionSectionId) {
            QuestionSectionEntity s = getEntityOrThrow(courseId, sectionId, bankId, questionSectionId);
            questionSectionRepository.delete(s);
            questionSectionRepository.flush();

            int order = 1;
            for (QuestionSectionEntity r : questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bankId)) {
                if (r.getSectionOrder() != order) r.setSectionOrder(order);
                order++;
            }
        }

        /**
         * NEW: rearranges the question sections of a bank (drag & drop, move up, move down).
         * The request must list EVERY section of the bank exactly once, with orders 1..n and no gaps.
         */
        @Transactional
        public List<QuestionDto.QuestionSectionResponse> reorder(Long courseId, Long sectionId, Long bankId,
                                                                 QuestionDto.SectionReorderRequest request) {
            bankService.getBankOrThrow(courseId, sectionId, bankId);
            List<QuestionSectionEntity> all = new ArrayList<>(
                    questionSectionRepository.findByQuestionBankIdOrderBySectionOrderAscIdAsc(bankId));
            if (all.size() < 2) {
                throw new ApiException("Reordering requires at least two sections in the question bank.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            List<QuestionDto.SectionReorderItem> items = request.getItems();
            if (items.size() != all.size()) {
                throw new ApiException("Reorder request must include every section of the question bank exactly once.",
                        HttpStatus.BAD_REQUEST);
            }

            Map<Long, QuestionSectionEntity> byId = all.stream()
                    .collect(Collectors.toMap(QuestionSectionEntity::getId, s -> s));
            Set<Long> seenIds = new HashSet<>();
            Set<Integer> seenOrders = new HashSet<>();
            for (QuestionDto.SectionReorderItem item : items) {
                if (item == null || item.getQuestionSectionId() == null || item.getNewOrder() == null) {
                    throw new ApiException("Reorder items must contain questionSectionId and newOrder.", HttpStatus.BAD_REQUEST);
                }
                if (!byId.containsKey(item.getQuestionSectionId())) {
                    throw new ApiException("Question section not found with id: " + item.getQuestionSectionId()
                            + " in question bank: " + bankId, HttpStatus.NOT_FOUND);
                }
                if (!seenIds.add(item.getQuestionSectionId()) || !seenOrders.add(item.getNewOrder())
                        || item.getNewOrder() > all.size()) {
                    throw new ApiException("Reorder request contains duplicate or out-of-range values.", HttpStatus.BAD_REQUEST);
                }
            }
            items.forEach(i -> byId.get(i.getQuestionSectionId()).setSectionOrder(i.getNewOrder()));

            all.sort(Comparator.comparingInt(QuestionSectionEntity::getSectionOrder));
            return all.stream().map(this::toResponse).toList();
        }

        // ---------- shared helper (also used by QuestionService) ----------

        /** Verifies the whole chain: course -> organize section -> bank -> question section. */
        public QuestionSectionEntity getEntityOrThrow(Long courseId, Long sectionId, Long bankId, Long questionSectionId) {
            bankService.getBankOrThrow(courseId, sectionId, bankId);
            return questionSectionRepository.findByIdAndQuestionBankId(questionSectionId, bankId)
                    .orElseThrow(() -> new ApiException(
                            "Question section not found with id: " + questionSectionId + " in question bank: " + bankId,
                            HttpStatus.NOT_FOUND));
        }

        private String validateName(String raw) {
            String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (name.isEmpty()) throw new ApiException("Section Name is required.", HttpStatus.BAD_REQUEST);
            if (name.length() < 3 || name.length() > 100) {
                throw new ApiException("Section Name must be between 3 and 100 characters.", HttpStatus.BAD_REQUEST);
            }
            return name;
        }

        /** "Section points" panel: Score 0..100 (optional), Feedback max 500 characters (optional). */
        private void validatePoints(Integer score, String feedback) {
            if (score != null && (score < MIN_SCORE || score > MAX_SCORE)) {
                throw new ApiException("Score must be between 0 and 100.", HttpStatus.BAD_REQUEST);
            }
            if (feedback != null && feedback.trim().length() > MAX_FEEDBACK_CHARS) {
                throw new ApiException("Feedback must not exceed 500 characters.", HttpStatus.BAD_REQUEST);
            }
        }

        private String blankToNull(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }

        private QuestionDto.QuestionSectionResponse toResponse(QuestionSectionEntity s) {
            return QuestionDto.QuestionSectionResponse.builder()
                    .id(s.getId())
                    .questionBankId(s.getQuestionBank().getId())
                    .name(s.getName())
                    .sectionOrder(s.getSectionOrder())
                    .score(s.getScore())
                    .feedback(s.getFeedback())
                    .questionCount(questionRepository.countByQuestionSectionId(s.getId()))
                    .createdAt(s.getCreatedAt())
                    .updatedAt(s.getUpdatedAt())
                    .build();
        }
    }

    /**
     * Questions live inside a QUESTION SECTION of a Question Bank:
     *   Course -> Section -> Question Bank -> Question Section -> Question
     * Every method takes the full path (courseId, sectionId, bankId, questionSectionId) and verifies the chain.
     */
    @Service
    @Transactional(readOnly = true)
    public static class QuestionService {

        private static final int MAX_QUESTIONS_PER_SECTION = 500;
        private static final int MAX_PAGE_SIZE = 50;
        private static final int MAX_KEYWORD_CHARS = 100;
        /** Idempotency-Key: the Angular clientId (UUID) or "<timestamp>-<random>" fallback. */
        private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

        private final QuestionRepository questionRepository;
        private final QuestionSectionService questionSectionService;
        private final QuestionValidator validator;
        private final QuestionMapper mapper;

        public QuestionService(QuestionRepository questionRepository,
                               QuestionSectionService questionSectionService,
                               QuestionValidator validator,
                               QuestionMapper mapper) {
            this.questionRepository = questionRepository;
            this.questionSectionService = questionSectionService;
            this.validator = validator;
            this.mapper = mapper;
        }

        // ---------- create / read ----------

        /** Create without an Idempotency-Key. */
        @Transactional
        public QuestionDto.QuestionResponse createQuestion(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                           QuestionDto.QuestionRequest request) {
            return createQuestion(courseId, sectionId, bankId, qsId, request, null);
        }

        /**
         * NEW: create with an optional Idempotency-Key. When a question with the same key already exists in this
         * question section, that question is returned and NOTHING is created (safe retry after a timeout).
         */
        @Transactional
        public QuestionDto.QuestionResponse createQuestion(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                           QuestionDto.QuestionRequest request, String idempotencyKey) {
            QuestionSectionEntity qs = scope(courseId, sectionId, bankId, qsId);
            String key = normalizeKey(idempotencyKey);

            if (key != null) {
                Optional<QuestionEntity> replay = questionRepository.findByQuestionSectionIdAndIdempotencyKey(qsId, key);
                if (replay.isPresent()) return mapper.toResponse(replay.get());
            }

            validator.validate(request);
            ensureCapacity(qsId, 1);

            QuestionEntity q = new QuestionEntity();
            q.setQuestionSection(qs);
            mapper.apply(q, request);
            q.setQuestionOrder(nextOrder(qsId));
            q.setIdempotencyKey(key);
            // flush now so a concurrent duplicate hits the unique index inside this call (the controller then replays it)
            return mapper.toResponse(questionRepository.saveAndFlush(q));
        }

        /** NEW: replay lookup used by the controller after a concurrent duplicate lost the race. */
        public Optional<QuestionDto.QuestionResponse> findByIdempotencyKey(Long courseId, Long sectionId, Long bankId,
                                                                           Long qsId, String idempotencyKey) {
            String key = normalizeKey(idempotencyKey);
            if (key == null) return Optional.empty();
            scope(courseId, sectionId, bankId, qsId);
            return questionRepository.findByQuestionSectionIdAndIdempotencyKey(qsId, key).map(mapper::toResponse);
        }

        /** Batch create (also used by CSV import). All-or-nothing. */
        @Transactional
        public List<QuestionDto.QuestionResponse> createQuestions(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                                  List<QuestionDto.QuestionRequest> requests) {
            QuestionSectionEntity qs = scope(courseId, sectionId, bankId, qsId);
            requests.forEach(validator::validate);
            ensureCapacity(qsId, requests.size());

            int order = nextOrder(qsId);
            List<QuestionEntity> entities = new ArrayList<>(requests.size());
            for (QuestionDto.QuestionRequest r : requests) {
                QuestionEntity q = new QuestionEntity();
                q.setQuestionSection(qs);
                mapper.apply(q, r);
                q.setQuestionOrder(order++);
                entities.add(q);
            }
            return questionRepository.saveAll(entities).stream().map(mapper::toResponse).toList();
        }

        public List<QuestionDto.QuestionResponse> getQuestions(Long courseId, Long sectionId, Long bankId, Long qsId) {
            scope(courseId, sectionId, bankId, qsId);
            return questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qsId)
                    .stream().map(mapper::toResponse).toList();
        }

        public QuestionDto.QuestionResponse getQuestion(Long courseId, Long sectionId, Long bankId, Long qsId, Long questionId) {
            scope(courseId, sectionId, bankId, qsId);
            return mapper.toResponse(getQuestionOrThrow(qsId, questionId));
        }

        // ---------- update / delete ----------

        @Transactional
        public QuestionDto.QuestionResponse updateQuestion(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                           Long questionId, QuestionDto.QuestionRequest request) {
            scope(courseId, sectionId, bankId, qsId);
            QuestionEntity q = getQuestionOrThrow(qsId, questionId);
            validator.validate(request);

            if (request.getVersion() != null && !request.getVersion().equals(q.getVersion())) {
                throw new ApiException("This question has been updated by another user. Please refresh to get the latest changes.",
                        HttpStatus.CONFLICT);
            }

            mapper.apply(q, request);
            q.setUpdatedAt(LocalDateTime.now()); // guarantees the row is dirty => @Version increments
            return mapper.toResponse(questionRepository.saveAndFlush(q));
        }

        @Transactional
        public void deleteQuestion(Long courseId, Long sectionId, Long bankId, Long qsId, Long questionId) {
            scope(courseId, sectionId, bankId, qsId);
            QuestionEntity q = getQuestionOrThrow(qsId, questionId);
            questionRepository.delete(q);

            List<QuestionEntity> remaining = new ArrayList<>(
                    questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qsId));
            remaining.removeIf(x -> x.getId().equals(questionId));
            renumber(remaining);
        }

        // ---------- duplicate / copy / move / hide ----------

        /** Duplicate = copy placed right after the original, same question section. */
        @Transactional
        public QuestionDto.QuestionResponse duplicateQuestion(Long courseId, Long sectionId, Long bankId, Long qsId, Long questionId) {
            scope(courseId, sectionId, bankId, qsId);
            QuestionEntity src = getQuestionOrThrow(qsId, questionId);
            ensureCapacity(qsId, 1);

            List<QuestionEntity> all = new ArrayList<>(
                    questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qsId));
            int idx = all.indexOf(src);

            QuestionEntity saved = questionRepository.save(
                    mapper.copyOf(src, src.getQuestionSection(), src.getQuestionOrder() + 1));
            all.add(idx + 1, saved);
            renumber(all);
            return mapper.toResponse(saved);
        }

        /** Copy = copy appended to the end of another question section of the SAME question bank. */
        @Transactional
        public QuestionDto.QuestionResponse copyQuestion(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                         Long questionId, Long targetQsId) {
            scope(courseId, sectionId, bankId, qsId);
            QuestionSectionEntity target = scope(courseId, sectionId, bankId, targetQsId);
            QuestionEntity src = getQuestionOrThrow(qsId, questionId);
            ensureCapacity(targetQsId, 1);

            QuestionEntity saved = questionRepository.save(mapper.copyOf(src, target, nextOrder(targetQsId)));
            return mapper.toResponse(saved);
        }

        /** Cut + paste = move to the end of another question section of the SAME question bank. */
        @Transactional
        public QuestionDto.QuestionResponse moveQuestion(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                         Long questionId, Long targetQsId) {
            scope(courseId, sectionId, bankId, qsId);
            if (qsId.equals(targetQsId)) {
                throw new ApiException("Target section must be different from the current section.", HttpStatus.BAD_REQUEST);
            }
            QuestionSectionEntity target = scope(courseId, sectionId, bankId, targetQsId);
            QuestionEntity q = getQuestionOrThrow(qsId, questionId);
            ensureCapacity(targetQsId, 1);

            int targetOrder = nextOrder(targetQsId);
            List<QuestionEntity> remaining = new ArrayList<>(
                    questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qsId));
            remaining.removeIf(x -> x.getId().equals(questionId));

            q.setQuestionSection(target);
            q.setQuestionOrder(targetOrder);
            renumber(remaining);
            return mapper.toResponse(questionRepository.saveAndFlush(q));
        }

        @Transactional
        public QuestionDto.QuestionResponse setHidden(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                      Long questionId, boolean hidden) {
            scope(courseId, sectionId, bankId, qsId);
            QuestionEntity q = getQuestionOrThrow(qsId, questionId);
            q.setHidden(hidden);
            q.setUpdatedAt(LocalDateTime.now());
            return mapper.toResponse(questionRepository.saveAndFlush(q));
        }

        // ---------- reorder ----------

        @Transactional
        public List<QuestionDto.QuestionResponse> reorderQuestions(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                                   QuestionDto.ReorderRequest request) {
            scope(courseId, sectionId, bankId, qsId);
            List<QuestionEntity> all = new ArrayList<>(
                    questionRepository.findByQuestionSectionIdOrderByQuestionOrderAscIdAsc(qsId));
            if (all.size() < 2) {
                throw new ApiException("Reordering requires at least two questions in the section.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            List<QuestionDto.ReorderItem> items = request.getItems();
            if (items.size() != all.size()) {
                throw new ApiException("Reorder request must include every question in the section exactly once.",
                        HttpStatus.BAD_REQUEST);
            }

            Map<Long, QuestionEntity> byId = all.stream().collect(Collectors.toMap(QuestionEntity::getId, q -> q));
            Set<Long> seenIds = new HashSet<>();
            Set<Integer> seenOrders = new HashSet<>();
            for (QuestionDto.ReorderItem item : items) {
                if (item == null || item.getQuestionId() == null || item.getNewOrder() == null) {
                    throw new ApiException("Reorder items must contain questionId and newOrder.", HttpStatus.BAD_REQUEST);
                }
                if (!byId.containsKey(item.getQuestionId())) {
                    throw new ApiException("Question not found with id: " + item.getQuestionId()
                            + " in section: " + qsId, HttpStatus.NOT_FOUND);
                }
                if (!seenIds.add(item.getQuestionId()) || !seenOrders.add(item.getNewOrder())
                        || item.getNewOrder() > all.size()) {
                    throw new ApiException("Reorder request contains duplicate or out-of-range values.", HttpStatus.BAD_REQUEST);
                }
            }
            items.forEach(i -> byId.get(i.getQuestionId()).setQuestionOrder(i.getNewOrder()));

            all.sort(Comparator.comparingInt(QuestionEntity::getQuestionOrder));
            return all.stream().map(mapper::toResponse).toList();
        }

        // ---------- existing questions (same Question Bank, other question sections) ----------

        public QuestionDto.ExistingQuestionsResponse searchExisting(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                                    String keyword, int page, int size) {
            scope(courseId, sectionId, bankId, qsId);
            int safePage = Math.max(page, 0);
            int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

            Page<QuestionEntity> result = questionRepository.searchExisting(
                    bankId, qsId, sanitizeKeyword(keyword), PageRequest.of(safePage, safeSize));

            return QuestionDto.ExistingQuestionsResponse.builder()
                    .items(result.getContent().stream().map(mapper::toResponse).toList())
                    .page(result.getNumber())
                    .size(result.getSize())
                    .totalElements(result.getTotalElements())
                    .totalPages(result.getTotalPages())
                    .build();
        }

        /** Adds COPIES of existing questions (same question bank only) to the end of this question section. */
        @Transactional
        public List<QuestionDto.QuestionResponse> addExistingQuestions(Long courseId, Long sectionId, Long bankId, Long qsId,
                                                                       QuestionDto.AddExistingRequest request) {
            QuestionSectionEntity qs = scope(courseId, sectionId, bankId, qsId);
            Set<Long> ids = new LinkedHashSet<>(request.getQuestionIds());

            Map<Long, QuestionEntity> found = questionRepository.findByIdsInBank(ids, bankId)
                    .stream().collect(Collectors.toMap(QuestionEntity::getId, q -> q));
            if (found.size() != ids.size()) {
                throw new ApiException("One or more selected questions were not found in this question bank.", HttpStatus.NOT_FOUND);
            }
            ensureCapacity(qsId, ids.size());

            int order = nextOrder(qsId);
            List<QuestionEntity> copies = new ArrayList<>(ids.size());
            for (Long id : ids) {
                copies.add(mapper.copyOf(found.get(id), qs, order++));
            }
            return questionRepository.saveAll(copies).stream().map(mapper::toResponse).toList();
        }

        // ---------- helpers ----------

        /** Verifies course -> organize section -> bank -> question section, returns the question section. */
        private QuestionSectionEntity scope(Long courseId, Long sectionId, Long bankId, Long qsId) {
            return questionSectionService.getEntityOrThrow(courseId, sectionId, bankId, qsId);
        }

        private QuestionEntity getQuestionOrThrow(Long qsId, Long questionId) {
            return questionRepository.findByIdAndQuestionSectionId(questionId, qsId)
                    .orElseThrow(() -> new ApiException(
                            "Question not found with id: " + questionId + " in section: " + qsId, HttpStatus.NOT_FOUND));
        }

        private int nextOrder(Long qsId) {
            Integer max = questionRepository.findMaxOrderByQuestionSectionId(qsId);
            return (max == null ? 0 : max) + 1;
        }

        private void ensureCapacity(Long qsId, int toAdd) {
            if (questionRepository.countByQuestionSectionId(qsId) + toAdd > MAX_QUESTIONS_PER_SECTION) {
                throw new ApiException("A section can contain at most " + MAX_QUESTIONS_PER_SECTION + " questions.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        private void renumber(List<QuestionEntity> ordered) {
            int order = 1;
            for (QuestionEntity q : ordered) {
                if (q.getQuestionOrder() != order) q.setQuestionOrder(order);
                order++;
            }
        }

        /** Blank header = no key (null). A malformed key is a 400, never silently ignored. */
        private String normalizeKey(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String key = raw.trim();
            if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
                throw new ApiException("Idempotency-Key must be 1-64 characters (letters, numbers, - _ . :).",
                        HttpStatus.BAD_REQUEST);
            }
            return key;
        }

        /** Lower-cases and strips LIKE wildcards so user input can't change the query's meaning. */
        private String sanitizeKeyword(String keyword) {
            if (keyword == null) return "";
            String k = keyword.trim().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "");
            return k.length() > MAX_KEYWORD_CHARS ? k.substring(0, MAX_KEYWORD_CHARS) : k;
        }
    }

    /** Entity <-> DTO mapping only. Assumes the request already passed {@link QuestionValidator}. */
    @Component
    public static class QuestionMapper {

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
                    .questionSectionId(q.getQuestionSection().getId())
                    .questionBankId(q.getQuestionSection().getQuestionBank().getId())
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
            com.example.lms.entity.QuestionType type = com.example.lms.entity.QuestionType.from(r.getType())
                    .orElseThrow(() -> new IllegalStateException("Request was not validated."));
            ScoringType scoring = type == com.example.lms.entity.QuestionType.SHORT_TEXT
                    ? ScoringType.BASIC
                    : ScoringType.from(r.getScoringType()).orElse(ScoringType.BASIC);

            q.setQuestionText(r.getText().trim());
            q.setQuestionType(type);
            q.setScoringType(scoring);
            q.setCorrectScore(zeroIfNull(r.getCorrectScore()));
            q.setIncorrectScore(zeroIfNull(r.getIncorrectScore()));
            q.setCorrectFeedback(blankToNull(r.getCorrectFeedback()));
            q.setIncorrectFeedback(blankToNull(r.getIncorrectFeedback()));
            q.setExpectedAnswer(type == com.example.lms.entity.QuestionType.SHORT_TEXT ? blankToNull(r.getExpectedAnswer()) : null);

            List<QuestionOptionEntity> options = new ArrayList<>();
            if (type != com.example.lms.entity.QuestionType.SHORT_TEXT) {
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

        /** Deep copy (new identity, version reset, NO idempotency key) used by duplicate / copy / add-existing. */
        public QuestionEntity copyOf(QuestionEntity src, QuestionSectionEntity target, int order) {
            QuestionEntity c = new QuestionEntity();
            c.setQuestionSection(target);
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

    /**
     * Import / Export of questions (SRS Field List #4 and #5).
     *
     * CSV columns: question,type,options,correct,correctScore,incorrectScore,correctFeedback,incorrectFeedback,expectedAnswer
     *  - options : option texts separated by '|'  (a literal '|' is written as '\|')
     *  - correct : 1-based option numbers separated by '|'   e.g. 1|3
     */
    @Service
    public static class QuestionTransferService {

        private static final Logger log = LoggerFactory.getLogger(QuestionTransferService.class);

        private static final long MAX_IMPORT_BYTES = 100L * 1024 * 1024;           // SRS: max 100 MB
        private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "csv", "xls", "xlsx");
        private static final int MAX_IMPORT_QUESTIONS = 1000;
        private static final int MAX_RECORD_CHARS = 20_000;
        private static final Pattern OPTION_SPLIT = Pattern.compile("(?<!\\\\)\\|");

        private static final List<String> HEADER = List.of("question", "type", "options", "correct",
                "correctScore", "incorrectScore", "correctFeedback", "incorrectFeedback", "expectedAnswer");

        private final QuestionService questionService;
        private final QuestionValidator validator;

        public QuestionTransferService(QuestionService questionService, QuestionValidator validator) {
            this.questionService = questionService;
            this.validator = validator;
        }

        // ---------- import ----------

        @Transactional
        public QuestionDto.ImportResponse importQuestions(Long courseId, Long sectionId, Long bankId, Long questionSectionId, MultipartFile file) {
            String ext = validateFile(file);
            if (!"csv".equals(ext)) {
                // Parsing PDF/DOC/DOCX/XLS/XLSX needs a library (POI / PDFBox) - not allowed by the "no new package" rule.
                throw new ApiException("Importing ." + ext + " files is not supported yet. Please use the CSV format.",
                        HttpStatus.NOT_IMPLEMENTED);
            }

            List<List<String>> records;
            try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
                records = com.example.lms.util.CsvUtil.parse(reader, MAX_IMPORT_QUESTIONS + 1, MAX_RECORD_CHARS);
            } catch (IllegalArgumentException e) {
                throw new ApiException(e.getMessage(), HttpStatus.BAD_REQUEST);
            } catch (IOException e) {
                log.error("Failed to read import file. originalFilename='{}'", file.getOriginalFilename(), e);
                throw new ApiException("Unable to read the uploaded file.", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            if (records.size() < 2) {
                throw new ApiException("The uploaded file does not contain any questions.", HttpStatus.BAD_REQUEST);
            }

            Map<String, Integer> cols = indexHeader(records.get(0));
            if (!cols.containsKey("question") || !cols.containsKey("type")) {
                throw new ApiException("The CSV must contain 'question' and 'type' columns.", HttpStatus.BAD_REQUEST);
            }

            List<QuestionDto.QuestionRequest> requests = new ArrayList<>(records.size() - 1);
            for (int i = 1; i < records.size(); i++) {
                int rowNumber = i + 1; // header is row 1
                try {
                    QuestionDto.QuestionRequest r = toRequest(records.get(i), cols);
                    validator.validate(r);
                    requests.add(r);
                } catch (ApiException e) {
                    throw new ApiException("Row " + rowNumber + ": " + e.getMessage(), HttpStatus.BAD_REQUEST);
                }
            }

            questionService.createQuestions(courseId, sectionId, bankId, questionSectionId, requests);
            return QuestionDto.ImportResponse.builder().imported(requests.size()).build();
        }

        private String validateFile(MultipartFile file) {
            if (file == null || file.isEmpty()) {
                throw new ApiException("Please select a file to import.", HttpStatus.BAD_REQUEST);
            }
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            int dot = name.lastIndexOf('.');
            String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new ApiException("Invalid file type", HttpStatus.BAD_REQUEST);
            }
            if (file.getSize() > MAX_IMPORT_BYTES) {
                throw new ApiException("File size exceeds 100 MB.", HttpStatus.BAD_REQUEST);
            }
            return ext;
        }

        private Map<String, Integer> indexHeader(List<String> header) {
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                map.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
            }
            return map;
        }

        private QuestionDto.QuestionRequest toRequest(List<String> row, Map<String, Integer> cols) {
            QuestionDto.QuestionRequest r = new QuestionDto.QuestionRequest();
            r.setText(cell(row, cols, "question"));
            r.setType(cell(row, cols, "type"));
            r.setCorrectScore(parseScore(cell(row, cols, "correctscore")));
            r.setIncorrectScore(parseScore(cell(row, cols, "incorrectscore")));
            r.setCorrectFeedback(cell(row, cols, "correctfeedback"));
            r.setIncorrectFeedback(cell(row, cols, "incorrectfeedback"));
            r.setExpectedAnswer(cell(row, cols, "expectedanswer"));

            List<QuestionDto.OptionRequest> options = new ArrayList<>();
            String rawOptions = cell(row, cols, "options");
            if (rawOptions != null && !rawOptions.isBlank()) {
                for (String part : OPTION_SPLIT.split(rawOptions)) {
                    QuestionDto.OptionRequest o = new QuestionDto.OptionRequest();
                    o.setText(part.replace("\\|", "|").trim());
                    options.add(o);
                }
            }

            String rawCorrect = cell(row, cols, "correct");
            if (rawCorrect != null && !rawCorrect.isBlank()) {
                for (String token : rawCorrect.split("\\|")) {
                    int idx;
                    try {
                        idx = Integer.parseInt(token.trim());
                    } catch (NumberFormatException e) {
                        throw new ApiException("'correct' must contain option numbers such as 1|3.", HttpStatus.BAD_REQUEST);
                    }
                    if (idx < 1 || idx > options.size()) {
                        throw new ApiException("'correct' refers to an option that does not exist.", HttpStatus.BAD_REQUEST);
                    }
                    options.get(idx - 1).setCorrect(true);
                }
            }
            r.setOptions(options);
            return r;
        }

        private String cell(List<String> row, Map<String, Integer> cols, String name) {
            Integer idx = cols.get(name);
            if (idx == null || idx >= row.size()) return null;
            String v = com.example.lms.util.CsvUtil.unprotect(row.get(idx));
            return v == null || v.isBlank() ? null : v.trim();
        }

        private Integer parseScore(String raw) {
            if (raw == null) return null;
            try {
                return Integer.valueOf(raw.trim());
            } catch (NumberFormatException e) {
                throw new ApiException("Score must be a whole number between 0 and 100.", HttpStatus.BAD_REQUEST);
            }
        }

        // ---------- export ----------

        @Transactional(readOnly = true)
        public byte[] exportQuestions(Long courseId, Long sectionId, Long bankId, Long questionSectionId) {
            List<QuestionDto.QuestionResponse> questions = questionService.getQuestions(courseId, sectionId, bankId, questionSectionId);

            StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel opens UTF-8 correctly
            sb.append(com.example.lms.util.CsvUtil.toLine(HEADER));
            for (QuestionDto.QuestionResponse q : questions) {
                List<String> options = new ArrayList<>();
                List<String> correct = new ArrayList<>();
                int n = 1;
                for (QuestionDto.OptionResponse o : q.getOptions()) {
                    options.add(o.getText().replace("|", "\\|"));
                    if (o.isCorrect()) correct.add(String.valueOf(n));
                    n++;
                }
                sb.append(com.example.lms.util.CsvUtil.toLine(List.of(
                        q.getText(),
                        q.getType() == null ? com.example.lms.entity.QuestionType.SHORT_TEXT.name() : q.getType().name(),
                        String.join("|", options),
                        String.join("|", correct),
                        String.valueOf(q.getCorrectScore()),
                        String.valueOf(q.getIncorrectScore()),
                        nullToEmpty(q.getCorrectFeedback()),
                        nullToEmpty(q.getIncorrectFeedback()),
                        nullToEmpty(q.getExpectedAnswer()))));
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        private String nullToEmpty(String v) {
            return v == null ? "" : v;
        }
    }
}