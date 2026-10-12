package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    String FILTER = " from Expense e where e.user.id = :uid and (:cat = '' or e.category = :cat)"
            + " and e.expenseDate >= :from and e.expenseDate <= :to"
            + " and (:q = '' or lower(e.description) like lower(concat('%', :q, '%')))";

    @Query(value = "select e" + FILTER, countQuery = "select count(e)" + FILTER)
    Page<Expense> search(@Param("uid") Long uid, @Param("cat") String cat, @Param("from") LocalDate from,
                         @Param("to") LocalDate to, @Param("q") String q, Pageable pageable);

    @Query("select e" + FILTER + " order by e.expenseDate desc, e.id desc")
    List<Expense> searchAll(@Param("uid") Long uid, @Param("cat") String cat, @Param("from") LocalDate from,
                            @Param("to") LocalDate to, @Param("q") String q);

    @Query("select coalesce(sum(e.amount), 0)" + FILTER)
    BigDecimal sumFiltered(@Param("uid") Long uid, @Param("cat") String cat, @Param("from") LocalDate from,
                           @Param("to") LocalDate to, @Param("q") String q);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    @Query("select distinct e.category from Expense e where e.user.id = :uid order by e.category")
    List<String> findCategories(@Param("uid") Long uid);

    // ---------- analytics ----------
    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.user.id = :uid and e.expenseDate between :from and :to")
    BigDecimal sumBetween(@Param("uid") Long uid, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(e) from Expense e where e.user.id = :uid and e.expenseDate between :from and :to")
    long countBetween(@Param("uid") Long uid, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.user.id = :uid and lower(e.category) = lower(:cat) and e.expenseDate between :from and :to")
    BigDecimal sumCategoryBetween(@Param("uid") Long uid, @Param("cat") String cat, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e.category, sum(e.amount), count(e) from Expense e where e.user.id = :uid and e.expenseDate between :from and :to group by e.category order by sum(e.amount) desc")
    List<Object[]> byCategory(@Param("uid") Long uid, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e.paymentMethod, sum(e.amount) from Expense e where e.user.id = :uid and e.expenseDate between :from and :to group by e.paymentMethod order by sum(e.amount) desc")
    List<Object[]> byPayment(@Param("uid") Long uid, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e.expenseDate, sum(e.amount) from Expense e where e.user.id = :uid and e.expenseDate between :from and :to group by e.expenseDate order by e.expenseDate")
    List<Object[]> daily(@Param("uid") Long uid, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select year(e.expenseDate), month(e.expenseDate), sum(e.amount) from Expense e where e.user.id = :uid and e.expenseDate between :from and :to group by year(e.expenseDate), month(e.expenseDate) order by year(e.expenseDate), month(e.expenseDate)")
    List<Object[]> monthly(@Param("uid") Long uid, @Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<Expense> findFirstByUserIdAndExpenseDateBetweenOrderByAmountDesc(Long uid, LocalDate from, LocalDate to);

    List<Expense> findTop5ByUserIdOrderByExpenseDateDescIdDesc(Long uid);
}
