package com.example.lms.service;

import com.example.lms.dto.QuestionDto;
import com.example.lms.entity.QuestionEntity;
import com.example.lms.entity.SectionEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.QuestionRepository;
import com.example.lms.repository.SectionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class QuestionService {

    private static final int MAX_QUESTIONS_PER_SECTION = 500;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_CHARS = 100;

    private final QuestionRepository questionRepository;
    private final SectionRepository sectionRepository;
    private final QuestionValidator validator;
    private final QuestionMapper mapper;

    public QuestionService(QuestionRepository questionRepository,
                           SectionRepository sectionRepository,
                           QuestionValidator validator,
                           QuestionMapper mapper) {
        this.questionRepository = questionRepository;
        this.sectionRepository = sectionRepository;
        this.validator = validator;
        this.mapper = mapper;
    }

    // ---------- create / read ----------

    @Transactional
    public QuestionDto.QuestionResponse createQuestion(Long courseId, Long sectionId,
                                                       QuestionDto.QuestionRequest request) {
        return createQuestions(courseId, sectionId, List.of(request)).get(0);
    }

    /** Batch create (also used by CSV import). All-or-nothing. */
    @Transactional
    public List<QuestionDto.QuestionResponse> createQuestions(Long courseId, Long sectionId,
                                                              List<QuestionDto.QuestionRequest> requests) {
        SectionEntity section = getSectionOrThrow(courseId, sectionId);
        requests.forEach(validator::validate);
        ensureCapacity(sectionId, requests.size());

        int order = nextOrder(sectionId);
        List<QuestionEntity> entities = new ArrayList<>(requests.size());
        for (QuestionDto.QuestionRequest r : requests) {
            QuestionEntity q = new QuestionEntity();
            q.setSection(section);
            mapper.apply(q, r);
            q.setQuestionOrder(order++);
            entities.add(q);
        }
        return questionRepository.saveAll(entities).stream().map(mapper::toResponse).toList();
    }

    public List<QuestionDto.QuestionResponse> getQuestions(Long courseId, Long sectionId) {
        getSectionOrThrow(courseId, sectionId);
        return questionRepository.findBySectionIdOrderByQuestionOrderAscIdAsc(sectionId)
                .stream().map(mapper::toResponse).toList();
    }

    public QuestionDto.QuestionResponse getQuestion(Long courseId, Long sectionId, Long questionId) {
        getSectionOrThrow(courseId, sectionId);
        return mapper.toResponse(getQuestionOrThrow(sectionId, questionId));
    }

    // ---------- update / delete ----------

    @Transactional
    public QuestionDto.QuestionResponse updateQuestion(Long courseId, Long sectionId, Long questionId,
                                                       QuestionDto.QuestionRequest request) {
        getSectionOrThrow(courseId, sectionId);
        QuestionEntity q = getQuestionOrThrow(sectionId, questionId);
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
    public void deleteQuestion(Long courseId, Long sectionId, Long questionId) {
        getSectionOrThrow(courseId, sectionId);
        QuestionEntity q = getQuestionOrThrow(sectionId, questionId);
        questionRepository.delete(q);

        List<QuestionEntity> remaining = new ArrayList<>(
                questionRepository.findBySectionIdOrderByQuestionOrderAscIdAsc(sectionId));
        remaining.removeIf(x -> x.getId().equals(questionId));
        renumber(remaining);
    }

    // ---------- duplicate / copy / move / hide ----------

    /** Duplicate = copy placed right after the original, same section. */
    @Transactional
    public QuestionDto.QuestionResponse duplicateQuestion(Long courseId, Long sectionId, Long questionId) {
        getSectionOrThrow(courseId, sectionId);
        QuestionEntity src = getQuestionOrThrow(sectionId, questionId);
        ensureCapacity(sectionId, 1);

        List<QuestionEntity> all = new ArrayList<>(
                questionRepository.findBySectionIdOrderByQuestionOrderAscIdAsc(sectionId));
        int idx = all.indexOf(src);

        QuestionEntity saved = questionRepository.save(mapper.copyOf(src, src.getSection(), src.getQuestionOrder() + 1));
        all.add(idx + 1, saved);
        renumber(all);
        return mapper.toResponse(saved);
    }

    /** Copy = copy appended to the end of another section of the same course. */
    @Transactional
    public QuestionDto.QuestionResponse copyQuestion(Long courseId, Long sectionId, Long questionId,
                                                     Long targetSectionId) {
        getSectionOrThrow(courseId, sectionId);
        SectionEntity target = getSectionOrThrow(courseId, targetSectionId);
        QuestionEntity src = getQuestionOrThrow(sectionId, questionId);
        ensureCapacity(targetSectionId, 1);

        QuestionEntity saved = questionRepository.save(
                mapper.copyOf(src, target, nextOrder(targetSectionId)));
        return mapper.toResponse(saved);
    }

    /** Cut + paste = move to the end of another section of the same course. */
    @Transactional
    public QuestionDto.QuestionResponse moveQuestion(Long courseId, Long sectionId, Long questionId,
                                                     Long targetSectionId) {
        getSectionOrThrow(courseId, sectionId);
        if (sectionId.equals(targetSectionId)) {
            throw new ApiException("Target section must be different from the current section.", HttpStatus.BAD_REQUEST);
        }
        SectionEntity target = getSectionOrThrow(courseId, targetSectionId);
        QuestionEntity q = getQuestionOrThrow(sectionId, questionId);
        ensureCapacity(targetSectionId, 1);

        int targetOrder = nextOrder(targetSectionId);
        List<QuestionEntity> remaining = new ArrayList<>(
                questionRepository.findBySectionIdOrderByQuestionOrderAscIdAsc(sectionId));
        remaining.removeIf(x -> x.getId().equals(questionId));

        q.setSection(target);
        q.setQuestionOrder(targetOrder);
        renumber(remaining);
        return mapper.toResponse(questionRepository.saveAndFlush(q));
    }

    @Transactional
    public QuestionDto.QuestionResponse setHidden(Long courseId, Long sectionId, Long questionId, boolean hidden) {
        getSectionOrThrow(courseId, sectionId);
        QuestionEntity q = getQuestionOrThrow(sectionId, questionId);
        q.setHidden(hidden);
        q.setUpdatedAt(LocalDateTime.now());
        return mapper.toResponse(questionRepository.saveAndFlush(q));
    }

    // ---------- reorder ----------

    @Transactional
    public List<QuestionDto.QuestionResponse> reorderQuestions(Long courseId, Long sectionId,
                                                               QuestionDto.ReorderRequest request) {
        getSectionOrThrow(courseId, sectionId);
        List<QuestionEntity> all = new ArrayList<>(
                questionRepository.findBySectionIdOrderByQuestionOrderAscIdAsc(sectionId));
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
                        + " in section: " + sectionId, HttpStatus.NOT_FOUND);
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

    // ---------- existing questions ----------

    public QuestionDto.ExistingQuestionsResponse searchExisting(Long courseId, Long sectionId,
                                                                String keyword, int page, int size) {
        getSectionOrThrow(courseId, sectionId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<QuestionEntity> result = questionRepository.searchExisting(
                courseId, sectionId, sanitizeKeyword(keyword), PageRequest.of(safePage, safeSize));

        return QuestionDto.ExistingQuestionsResponse.builder()
                .items(result.getContent().stream().map(mapper::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /** Adds COPIES of existing questions (same course only) to the end of this section. */
    @Transactional
    public List<QuestionDto.QuestionResponse> addExistingQuestions(Long courseId, Long sectionId,
                                                                   QuestionDto.AddExistingRequest request) {
        SectionEntity section = getSectionOrThrow(courseId, sectionId);
        Set<Long> ids = new LinkedHashSet<>(request.getQuestionIds());

        Map<Long, QuestionEntity> found = questionRepository.findByIdInAndSectionCourseId(ids, courseId)
                .stream().collect(Collectors.toMap(QuestionEntity::getId, q -> q));
        if (found.size() != ids.size()) {
            throw new ApiException("One or more selected questions were not found in this course.", HttpStatus.NOT_FOUND);
        }
        ensureCapacity(sectionId, ids.size());

        int order = nextOrder(sectionId);
        List<QuestionEntity> copies = new ArrayList<>(ids.size());
        for (Long id : ids) {
            copies.add(mapper.copyOf(found.get(id), section, order++));
        }
        return questionRepository.saveAll(copies).stream().map(mapper::toResponse).toList();
    }

    // ---------- helpers ----------

    private SectionEntity getSectionOrThrow(Long courseId, Long sectionId) {
        return sectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new ApiException(
                        "Section not found with id: " + sectionId + " in course: " + courseId, HttpStatus.NOT_FOUND));
    }

    private QuestionEntity getQuestionOrThrow(Long sectionId, Long questionId) {
        return questionRepository.findByIdAndSectionId(questionId, sectionId)
                .orElseThrow(() -> new ApiException(
                        "Question not found with id: " + questionId + " in section: " + sectionId, HttpStatus.NOT_FOUND));
    }

    private int nextOrder(Long sectionId) {
        Integer max = questionRepository.findMaxOrderBySectionId(sectionId);
        return (max == null ? 0 : max) + 1;
    }

    private void ensureCapacity(Long sectionId, int toAdd) {
        if (questionRepository.countBySectionId(sectionId) + toAdd > MAX_QUESTIONS_PER_SECTION) {
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

    /** Lower-cases and strips LIKE wildcards so user input can't change the query's meaning. */
    private String sanitizeKeyword(String keyword) {
        if (keyword == null) return "";
        String k = keyword.trim().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "");
        return k.length() > MAX_KEYWORD_CHARS ? k.substring(0, MAX_KEYWORD_CHARS) : k;
    }
}