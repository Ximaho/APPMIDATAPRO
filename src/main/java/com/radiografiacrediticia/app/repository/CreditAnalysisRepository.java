package com.radiografiacrediticia.app.repository;

import com.radiografiacrediticia.app.model.CreditAnalysis;
import com.radiografiacrediticia.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditAnalysisRepository extends JpaRepository<CreditAnalysis, Long> {

    List<CreditAnalysis> findByUserOrderByCreatedAtDesc(User user);

    /** Busca un análisis garantizando que pertenece al usuario indicado (evita IDOR). */
    Optional<CreditAnalysis> findByIdAndUser(Long id, User user);
}
