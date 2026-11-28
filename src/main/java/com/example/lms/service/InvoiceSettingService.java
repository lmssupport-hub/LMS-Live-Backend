package com.example.lms.service;

import com.example.lms.dto.InvoiceSettingDto;
import com.example.lms.entity.InvoiceSettingEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.InvoiceSettingRepository;
import com.example.lms.util.InvoiceLabels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Invoice setup of an organization (one per company admin).
 *
 * Only the company admin edits it (the controller is ADMIN-only). It is stored per organization, so the same
 * setup is what every invoice / credit note of that company will use later (see findForOrganization).
 */
@Service
public class InvoiceSettingService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSettingService.class);

    /** Label keys the Angular page offers - same keys as textGroups in invoice-setup.ts. */
    static final Set<String> KNOWN_TEXT_KEYS = InvoiceLabels.DEFAULTS.keySet();

    private final InvoiceSettingRepository repository;
    private final CourseAccessService access;

    public InvoiceSettingService(InvoiceSettingRepository repository, CourseAccessService access) {
        this.repository = repository;
        this.access = access;
    }

    /** The saved setup of the caller's organization, or empty when the admin has not saved one yet. */
    @Transactional(readOnly = true)
    public Optional<InvoiceSettingDto.Response> getCurrent() {
        return findForOrganization(access.currentOrganizationId()).map(InvoiceSettingMapper::toResponse);
    }

    /** Used later when an invoice / credit note is generated: the setup of the buyer's organization. */
    @Transactional(readOnly = true)
    public Optional<InvoiceSettingEntity> findForOrganization(Long organizationId) {
        return repository.findByOrganizationId(organizationId);
    }

    /** Creates the setup the first time, replaces it afterwards (one setup per organization). */
    @Transactional
    public InvoiceSettingDto.Response save(InvoiceSettingDto.SaveRequest request) {
        User admin = access.currentUser();
        Long organizationId = access.currentOrganizationId();

        validate(request);

        InvoiceSettingEntity entity = repository.findByOrganizationId(organizationId).orElse(null);
        if (entity == null) {
            entity = new InvoiceSettingEntity();
            entity.setOrganizationId(organizationId);
        }
        InvoiceSettingMapper.apply(request, entity);
        entity.setUpdatedBy(admin.getId());
        try {
            InvoiceSettingEntity saved = repository.saveAndFlush(entity);
            log.info("Invoice setup saved: organizationId={}, userId={}", organizationId, admin.getId());
            return InvoiceSettingMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // two saves of a brand new setup at the same moment: the unique organization row already exists
            throw new ApiException("The invoice setup was saved by someone else just now. Please try again.", HttpStatus.CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // application level validation: rules that need several fields
    // ------------------------------------------------------------------

    void validate(InvoiceSettingDto.SaveRequest r) {
        if (r.isShowLogo() && (r.getLogo() == null || r.getLogo().isBlank())) {
            throw new ApiException("Upload a logo, or choose \"Do not show a logo\".", HttpStatus.BAD_REQUEST);
        }

        String invoicePrefix = r.getInvoicePrefix() == null ? "" : r.getInvoicePrefix().trim();
        String creditPrefix = r.getCreditNotePrefix() == null ? "" : r.getCreditNotePrefix().trim();
        if (!invoicePrefix.isEmpty() && invoicePrefix.equalsIgnoreCase(creditPrefix)) {
            throw new ApiException("Invoice prefix and credit note prefix must be different.", HttpStatus.BAD_REQUEST);
        }

        if (r.getTexts() != null) {
            for (String key : r.getTexts().keySet()) {
                if (!KNOWN_TEXT_KEYS.contains(key)) {
                    throw new ApiException("Unknown invoice text \"" + key + "\".", HttpStatus.BAD_REQUEST);
                }
            }
        }
    }
}