package com.example.lineiphone_easyinstallments.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "promotion_prices")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🌟 ข้อมูลสำหรับการค้นหา (Condition)
    @Column(name = "device_model", nullable = false)
    private String model;         // เช่น "15 Pro Max", "17", "iPad Gen 11 Wi-Fi"

    @Column(name = "capacity", nullable = false)
    private String capacity;      // เช่น "128GB", "256GB", "512GB"

    @Column(name = "device_condition", nullable = false)
    private String condition;     // เช่น "มือ 1", "มือ 2"

    // 🌟 ข้อมูลราคาและยอดผ่อน (Price & Installments)
    @Column(name = "down_payment", nullable = false)
    private Integer downPayment;  // ยอดดาวน์

    // ยอดส่งรายเดือน (ใส่ Integer เพราะบางรุ่นอาจไม่มีผ่อนบางเดือน จะได้เป็น null ได้)
    @Column(name = "month_10")
    private Integer month10;

    @Column(name = "month_12")
    private Integer month12;

    @Column(name = "month_15")
    private Integer month15;      // สำหรับมือ 2

    @Column(name = "month_18")
    private Integer month18;      // สำหรับโปรโมชั่นมือ 1 แบบ 18 เดือน

    @Column(name = "month_24")
    private Integer month24;      // สำหรับ iPhone 17 Series แบบยาว
}