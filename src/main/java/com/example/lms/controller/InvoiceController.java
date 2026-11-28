package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.InvoiceDto;
import com.example.lms.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Learner side: purchase history (Profile > Purchase History) and invoice PDF download.
 * Each user only ever gets their own purchases / invoices (checked in InvoiceService).
 */
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping("/my-purchases")
    @Operation(summary = "My paid courses (newest first) with the invoice of each one")
    public ResponseEntity<ApiResponse<List<InvoiceDto.PurchaseItem>>> myPurchases() {
        return ResponseEntity.ok(ApiResponse.success("Purchases fetched successfully.", service.myPurchases()));
    }

    @GetMapping("/invoices/{id}/pdf")
    @Operation(summary = "Download one of my invoices as a PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        InvoiceDto.PdfFile file = service.pdfFor(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}