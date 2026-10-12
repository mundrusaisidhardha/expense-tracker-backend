package com.expensetracker.service;

import com.expensetracker.dto.Dtos.ExpenseDto;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
public class AnalyticsService {
    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final String cur;

    public AnalyticsService(ExpenseRepository expenses, BudgetRepository budgets,
                            @Value("${app.currency-symbol:₹}") String cur) {
        this.expenses = expenses;
        this.budgets = budgets;
        this.cur = cur;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static BigDecimal bd(Object o) { return o == null ? BigDecimal.ZERO : (BigDecimal) o; }

    private String money(BigDecimal v) { return cur + String.format("%,.0f", v); }

    /** Budget progress for the current month. */
    public List<Map<String, Object>> budgetStatus(User u) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today.withDayOfMonth(today.lengthOfMonth());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Budget b : budgets.findByUserIdOrderByCategoryAsc(u.getId())) {
            BigDecimal spent = "ALL".equalsIgnoreCase(b.getCategory())
                    ? expenses.sumBetween(u.getId(), start, end)
                    : expenses.sumCategoryBetween(u.getId(), b.getCategory(), start, end);
            double pct = spent.multiply(BigDecimal.valueOf(100))
                    .divide(b.getMonthlyLimit(), 1, RoundingMode.HALF_UP).doubleValue();
            String status = pct >= 100 ? "EXCEEDED" : pct >= 80 ? "WARNING" : "OK";
            out.add(map("id", b.getId(), "category", b.getCategory(), "limit", b.getMonthlyLimit(),
                    "spent", spent, "percent", pct, "status", status));
        }
        return out;
    }

    public Map<String, Object> summary(User u, LocalDate from, LocalDate to) {
        Long uid = u.getId();
        LocalDate today = LocalDate.now();

        BigDecimal total = expenses.sumBetween(uid, from, to);
        long count = expenses.countBetween(uid, from, to);
        BigDecimal average = count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        // category breakdown
        List<Map<String, Object>> byCategory = new ArrayList<>();
        for (Object[] r : expenses.byCategory(uid, from, to)) {
            BigDecimal t = bd(r[1]);
            double pct = total.signum() == 0 ? 0 : t.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
            byCategory.add(map("category", r[0], "total", t, "count", r[2], "percent", pct));
        }

        // payment methods
        List<Map<String, Object>> payments = new ArrayList<>();
        for (Object[] r : expenses.byPayment(uid, from, to))
            payments.add(map("method", r[0] == null ? "Other" : r[0], "total", bd(r[1])));

        // daily + weekday pattern
        List<Map<String, Object>> daily = new ArrayList<>();
        BigDecimal[] weekday = new BigDecimal[7];
        Arrays.fill(weekday, BigDecimal.ZERO);
        for (Object[] r : expenses.daily(uid, from, to)) {
            LocalDate d = (LocalDate) r[0];
            BigDecimal t = bd(r[1]);
            daily.add(map("date", d.toString(), "total", t));
            int idx = d.getDayOfWeek().getValue() - 1;
            weekday[idx] = weekday[idx].add(t);
        }

        // last 12 months (zero-filled)
        LocalDate m0 = today.minusMonths(11).withDayOfMonth(1);
        Map<String, BigDecimal> monthMap = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            LocalDate d = m0.plusMonths(i);
            monthMap.put(String.format("%04d-%02d", d.getYear(), d.getMonthValue()), BigDecimal.ZERO);
        }
        for (Object[] r : expenses.monthly(uid, m0, today.withDayOfMonth(today.lengthOfMonth()))) {
            String k = String.format("%04d-%02d", ((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            monthMap.put(k, bd(r[2]));
        }
        List<Map<String, Object>> monthly = new ArrayList<>();
        monthMap.forEach((k, v) -> monthly.add(map("month", k, "total", v)));

        // this month vs last month
        LocalDate ms = today.withDayOfMonth(1);
        BigDecimal thisMonth = expenses.sumBetween(uid, ms, today.withDayOfMonth(today.lengthOfMonth()));
        BigDecimal lastMonth = expenses.sumBetween(uid, ms.minusMonths(1), ms.minusDays(1));
        Double change = lastMonth.signum() == 0 ? null
                : thisMonth.subtract(lastMonth).multiply(BigDecimal.valueOf(100))
                    .divide(lastMonth, 1, RoundingMode.HALF_UP).doubleValue();
        BigDecimal projected = thisMonth.multiply(BigDecimal.valueOf(today.lengthOfMonth()))
                .divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_UP);

        Map<String, Object> highest = expenses.findFirstByUserIdAndExpenseDateBetweenOrderByAmountDesc(uid, from, to)
                .map(e -> map("amount", e.getAmount(), "category", e.getCategory(),
                        "description", e.getDescription(), "date", e.getExpenseDate().toString()))
                .orElse(null);

        List<ExpenseDto> recent = new ArrayList<>();
        for (Expense e : expenses.findTop5ByUserIdOrderByExpenseDateDescIdDesc(uid)) recent.add(ExpenseDto.from(e));

        List<Map<String, Object>> budgetList = budgetStatus(u);

        // ---------- smart insights ----------
        List<String> insights = new ArrayList<>();
        if (!byCategory.isEmpty()) {
            Map<String, Object> top = byCategory.get(0);
            insights.add("Your biggest spending category is " + top.get("category") + " (" + top.get("percent")
                    + "% of the selected period, " + money(bd(top.get("total"))) + ").");
        }
        if (change != null) {
            insights.add(change > 0
                    ? "You have spent " + Math.abs(change) + "% more this month than last month."
                    : change < 0 ? "Nice! You have spent " + Math.abs(change) + "% less this month than last month."
                    : "Your spending this month matches last month.");
        }
        if (thisMonth.signum() > 0)
            insights.add("At this pace you will spend about " + money(projected) + " by the end of the month.");
        int maxIdx = 0;
        for (int i = 1; i < 7; i++) if (weekday[i].compareTo(weekday[maxIdx]) > 0) maxIdx = i;
        if (weekday[maxIdx].signum() > 0) {
            String dn = DayOfWeek.of(maxIdx + 1).name();
            insights.add("You tend to spend the most on " + dn.charAt(0) + dn.substring(1).toLowerCase() + "s.");
        }
        for (Map<String, Object> b : budgetList) {
            if ("EXCEEDED".equals(b.get("status")))
                insights.add("Budget exceeded for " + b.get("category") + ": " + money(bd(b.get("spent"))) + " of " + money(bd(b.get("limit"))) + ".");
            else if ("WARNING".equals(b.get("status")))
                insights.add("You have used " + b.get("percent") + "% of your " + b.get("category") + " budget.");
        }
        if (insights.isEmpty()) insights.add("Add some expenses to see personalised insights.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("count", count);
        out.put("average", average);
        out.put("highest", highest);
        out.put("thisMonth", thisMonth);
        out.put("lastMonth", lastMonth);
        out.put("changePercent", change);
        out.put("projectedMonthEnd", projected);
        out.put("topCategory", byCategory.isEmpty() ? null : byCategory.get(0).get("category"));
        out.put("byCategory", byCategory);
        out.put("payments", payments);
        out.put("daily", daily);
        out.put("weekday", Arrays.asList(weekday));
        out.put("monthly", monthly);
        out.put("budgets", budgetList);
        out.put("insights", insights);
        out.put("recent", recent);
        return out;
    }
}
