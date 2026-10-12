package com.expensetracker.dto;

import com.expensetracker.model.Expense;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 6, max = 100, message = "Password must be at least 6 characters") String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, String name, String email) {}

    public record ExpenseRequest(@NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than 0") BigDecimal amount,
                                 @NotBlank @Size(max = 60) String category,
                                 @Size(max = 255) String description,
                                 @NotNull LocalDate expenseDate,
                                 @Size(max = 30) String paymentMethod) {}

    public record ExpenseDto(Long id, BigDecimal amount, String category, String description,
                             LocalDate expenseDate, String paymentMethod) {
        public static ExpenseDto from(Expense e) {
            return new ExpenseDto(e.getId(), e.getAmount(), e.getCategory(), e.getDescription(),
                    e.getExpenseDate(), e.getPaymentMethod());
        }
    }

    public record BudgetRequest(@NotBlank @Size(max = 60) String category,
                                @NotNull @DecimalMin(value = "1", message = "Limit must be at least 1") BigDecimal monthlyLimit) {}
}
