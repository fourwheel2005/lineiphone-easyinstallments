package com.example.lineiphone_easyinstallments.repository;

import com.example.lineiphone_easyinstallments.entity.PromotionPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromotionPriceRepository extends JpaRepository<PromotionPrice, Long> {

    Optional<PromotionPrice> findByModelAndCapacityAndCondition(String model, String capacity, String condition);

}