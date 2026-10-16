package com.example.lms.service;

import com.example.lms.dto.QuestionDto;
import com.example.lms.entity.QuestionType;
import com.example.lms.exception.ApiException;
import com.example.lms.util.CsvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Import / Export of questions (SRS Field List #4 and #5).
 *
 * CSV columns: question,type,options,correct,correctScore,incorrectScore,correctFeedback,incorrectFeedback,expectedAnswer
 *  - options : option texts separated by '|'  (a literal '|' is written as '\|')
 *  - correct : 1-based option numbers separated by '|'   e.g. 1|3
 */
@Service
public class QuestionTransferService {

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
    public QuestionDto.ImportResponse importQuestions(Long courseId, Long sectionId, MultipartFile file) {
        String ext = validateFile(file);
        if (!"csv".equals(ext)) {
            // Parsing PDF/DOC/DOCX/XLS/XLSX needs a library (POI / PDFBox) - not allowed by the "no new package" rule.
            throw new ApiException("Importing ." + ext + " files is not supported yet. Please use the CSV format.",
                    HttpStatus.NOT_IMPLEMENTED);
        }

        List<List<String>> records;
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            records = CsvUtil.parse(reader, MAX_IMPORT_QUESTIONS + 1, MAX_RECORD_CHARS);
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

        questionService.createQuestions(courseId, sectionId, requests);
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
        String v = CsvUtil.unprotect(row.get(idx));
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
    public byte[] exportQuestions(Long courseId, Long sectionId) {
        List<QuestionDto.QuestionResponse> questions = questionService.getQuestions(courseId, sectionId);

        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM so Excel opens UTF-8 correctly
        sb.append(CsvUtil.toLine(HEADER));
        for (QuestionDto.QuestionResponse q : questions) {
            List<String> options = new ArrayList<>();
            List<String> correct = new ArrayList<>();
            int n = 1;
            for (QuestionDto.OptionResponse o : q.getOptions()) {
                options.add(o.getText().replace("|", "\\|"));
                if (o.isCorrect()) correct.add(String.valueOf(n));
                n++;
            }
            sb.append(CsvUtil.toLine(List.of(
                    q.getText(),
                    q.getType() == null ? QuestionType.SHORT_TEXT.name() : q.getType().name(),
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