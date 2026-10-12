package com.expensetracker.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.expensetracker.dto.Dtos.BudgetRequest;
import com.expensetracker.model.Budget;
import com.expensetracker.model.User;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.service.AnalyticsService;
import com.expensetracker.service.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetRepository repo;
    private final AnalyticsService analytics;
    private final CurrentUser current;

    public BudgetController(BudgetRepository repo, AnalyticsService analytics, CurrentUser current) {
        this.repo = repo;
        this.analytics = analytics;
        this.current = current;
    }

    @GetMapping
    public List<Map<String, Object>> list() { return analytics.budgetStatus(current.get()); }

    /** Create or update (one budget per category). */
    @PostMapping
    public List<Map<String, Object>> save(@Valid @RequestBody BudgetRequest req) {
        User u = current.get();
        String cat = req.category().trim();
        if (cat.equalsIgnoreCase("all")) cat = "ALL";
        Budget b = repo.findByUserIdAndCategoryIgnoreCase(u.getId(), cat).orElseGet(Budget::new);
        b.setUser(u);
        b.setCategory(cat);
        b.setMonthlyLimit(req.monthlyLimit());
        repo.save(b);
        return analytics.budgetStatus(u);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Budget b = repo.findByIdAndUserId(id, current.get().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        repo.delete(b);
    }
}
