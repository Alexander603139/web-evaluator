package com.webevaluator.reporting.repository;

import com.webevaluator.core.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {}