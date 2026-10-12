package com.expensetracker.repository;

import com.expensetracker.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserIdOrderByCategoryAsc(Long userId);
    Optional<Budget> findByUserIdAndCategoryIgnoreCase(Long userId, String category);
    Optional<Budget> findByIdAndUserId(Long id, Long userId);
}
