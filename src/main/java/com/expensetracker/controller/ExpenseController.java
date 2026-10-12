package com.expensetracker.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.expensetracker.dto.Dtos.ExpenseDto;
import com.expensetracker.dto.Dtos.ExpenseRequest;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.service.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX = LocalDate.of(2999, 12, 31);
    private static final Set<String> SORTABLE = Set.of("expenseDate", "amount", "category");

    private final ExpenseRepository repo;
    private final CurrentUser current;

    public ExpenseController(ExpenseRepository repo, CurrentUser current) {
        this.repo = repo;
        this.current = current;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String category,
                                    @RequestParam(defaultValue = "") String q,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "expenseDate") String sortBy,
                                    @RequestParam(defaultValue = "desc") String dir,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        User u = current.get();
        LocalDate f = from == null ? MIN : from;
        LocalDate t = to == null ? MAX : to;
        if (!SORTABLE.contains(sortBy)) {
            sortBy = "expenseDate";
        }
        Sort sort = Sort.by("asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy)
                .and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);

        Page<Expense> result = repo.search(u.getId(), category.trim(), f, t, q.trim(), pageable);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", result.getContent().stream().map(ExpenseDto::from).toList());
        out.put("page", result.getNumber());
        out.put("size", result.getSize());
        out.put("totalPages", result.getTotalPages());
        out.put("totalElements", result.getTotalElements());
        out.put("totalAmount", repo.sumFiltered(u.getId(), category.trim(), f, t, q.trim()));
        return out;
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return repo.findCategories(current.get().getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDto create(@Valid @RequestBody ExpenseRequest req) {
        Expense e = new Expense();
        e.setUser(current.get());
        apply(e, req);
        return ExpenseDto.from(repo.save(e));
    }

    @PutMapping("/{id}")
    public ExpenseDto update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest req) {
        Expense e = find(id);
        apply(e, req);
        return ExpenseDto.from(repo.save(e));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repo.delete(find(id));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "") String category,
                                        @RequestParam(defaultValue = "") String q,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        User u = current.get();
        List<Expense> rows = repo.searchAll(u.getId(), category.trim(), from == null ? MIN : from, to == null ? MAX : to, q.trim());
        StringBuilder sb = new StringBuilder("Date,Category,Description,Payment Method,Amount\n");
        for (Expense e : rows) {
            sb.append(e.getExpenseDate())
                    .append(',')
                    .append(csv(e.getCategory()))
                    .append(',')
                    .append(csv(e.getDescription()))
                    .append(',')
                    .append(csv(e.getPaymentMethod()))
                    .append(',')
                    .append(e.getAmount())
                    .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=expenses.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }

    private Expense find(Long id) {
        return repo.findByIdAndUserId(id, current.get().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    private void apply(Expense e, ExpenseRequest r) {
        e.setAmount(r.amount());
        e.setCategory(r.category().trim());
        e.setDescription(r.description() == null ? null : r.description().trim());
        e.setExpenseDate(r.expenseDate());
        e.setPaymentMethod(r.paymentMethod() == null || r.paymentMethod().isBlank() ? "Other" : r.paymentMethod().trim());
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
