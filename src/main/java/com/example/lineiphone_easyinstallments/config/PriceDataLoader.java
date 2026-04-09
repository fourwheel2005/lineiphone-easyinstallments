package com.example.lineiphone_easyinstallments.config;

import com.example.lineiphone_easyinstallments.entity.PromotionPrice;
import com.example.lineiphone_easyinstallments.repository.PromotionPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceDataLoader implements CommandLineRunner {

    private final PromotionPriceRepository promotionPriceRepository;

    @Override
    @Transactional
    public void run(String... args) {

        promotionPriceRepository.deleteAll();
        log.info("📥 กำลังเคลียร์ฐานข้อมูลและนำเข้าข้อมูลโปรโมชั่นชุดใหม่ทั้งหมด...");


        addPrice("17", "256GB", "มือ 1", 9900, 2690, 2490, null, 1990, 1690);
        addPrice("17", "512GB", "มือ 1", 17900, 2990, 2790, null, null, null);
        addPrice("17 Air", "256GB", "มือ 1", 17890, 3190, 2890, null, null, null);
        addPrice("17 Air", "512GB", "มือ 1", 23900, 3490, 2990, null, null, null);
        addPrice("17 Pro", "256GB", "มือ 1", 16900, 3390, 2990, null, 2390, 2090);
        addPrice("17 Pro", "512GB", "มือ 1", 25900, 3490, 3190, null, null, null);
        addPrice("17 Pro Max", "256GB", "มือ 1", 18900, 3690, 3190, null, 2690, 2290);
        addPrice("17 Pro Max", "512GB", "มือ 1", 27900, 3790, 3290, null, null, null);

        // ==========================================
        // 📱 2. ตารางผ่อน iPhone มือ 1 (รุ่น 13 - 16)
        // ==========================================
        addPrice("13", "128GB", "มือ 1", 1990, null, 2090, null, 1790, null);
        addPrice("14", "128GB", "มือ 1", 3890, 2290, 1990, null, 1790, null);
        addPrice("15", "128GB", "มือ 1", 4990, 2390, 2190, null, 1790, null);
        addPrice("16", "128GB", "มือ 1", 7890, 2490, 2290, null, 1890, null);
        addPrice("16 Plus", "128GB", "มือ 1", 10900, 2690, 2490, null, 2090, null);

        // ==========================================
        // ♻️ 3. ตารางผ่อน iPhone มือ 2
        // ==========================================
        addPrice("13", "128GB", "มือ 2", 1490, null, 1790, 1590, null, null);
        addPrice("13 Pro", "128GB", "มือ 2", 2590, 2190, 1890, 1690, null, null);
        addPrice("13 Pro Max", "128GB", "มือ 2", 2990, 2390, 2190, 1890, null, null);
        addPrice("13 Pro Max", "256GB", "มือ 2", 2990, 2490, 2290, 1990, null, null);

        addPrice("14", "128GB", "มือ 2", 2990, 2190, 1990, 1790, null, null);
        addPrice("14 Pro", "128GB", "มือ 2", 5590, 2290, 1990, 1790, null, null);
        addPrice("14 Pro Max", "128GB", "มือ 2", 6590, 2290, 1990, 1790, null, null);

        addPrice("15", "128GB", "มือ 2", 3590, 2190, 1990, 1790, null, null);
        addPrice("15", "256GB", "มือ 2", 3990, 2290, 1990, 1790, null, null);
        addPrice("15 Plus", "256GB", "มือ 2", 6590, 2490, 2190, 1890, null, null);
        addPrice("15 Pro", "128GB", "มือ 2", 6990, 2490, 2190, 1890, null, null);
        addPrice("15 Pro", "256GB", "มือ 2", 7490, 2590, 2290, 1990, null, null);
        addPrice("15 Pro Max", "256GB", "มือ 2", 8990, 2790, 2490, 2190, null, null);

        addPrice("16", "128GB", "มือ 2", 5590, 2290, 1990, 1790, null, null);
        addPrice("16 Plus", "128GB", "มือ 2", 7900, 2890, 2690, 2390, null, null);
        addPrice("16 Plus", "256GB", "มือ 2", 7900, 2990, 2790, 2490, null, null);
        addPrice("16 Pro", "128GB", "มือ 2", 9900, 2990, 2590, 2290, null, null);
        addPrice("16 Pro Max", "256GB", "มือ 2", 11900, 3090, 2790, 2490, null, null);
        addPrice("16 Pro Max", "512GB", "มือ 2", 12900, 3190, 2890, 2490, null, null);

        addPrice("17", "256GB", "มือ 2", 8990, 2490, 2290, 1890, null, null);
        addPrice("17 Air", "256GB", "มือ 2", 8990, 2790, 2490, 2190, null, null);

        // ==========================================
        // 💻 4. ตารางผ่อน iPad มือ 1
        // ==========================================
        addPrice("iPad Gen 11", "128GB", "มือ 1", 3790, 1790, null, null, null, null);
        addPrice("iPad Gen 11", "256GB", "มือ 1", 4990, 1990, null, null, null, null);
        addPrice("iPad mini 7", "128GB", "มือ 1", 5990, 1990, null, null, null, null);
        addPrice("iPad mini 7 Cellular", "128GB", "มือ 1", 7990, 2290, null, null, null, null);
        addPrice("iPad Air 7", "128GB", "มือ 1", 8990, 2390, 1990, null, null, null);
        addPrice("iPad Air 7", "256GB", "มือ 1", 11900, 2690, 2390, null, null, null);



        log.info("✅ นำเข้าข้อมูลราคาโปรโมชั่นทั้งหมดสำเร็จแล้ว พร้อมลุย!!");
    }


    private void addPrice(String model, String cap, String cond, Integer down,
                          Integer m10, Integer m12, Integer m15, Integer m18, Integer m24) {
        PromotionPrice p = new PromotionPrice();
        p.setModel(model);
        p.setCapacity(cap);
        p.setCondition(cond);
        p.setDownPayment(down);
        p.setMonth10(m10);
        p.setMonth12(m12);
        p.setMonth15(m15);
        p.setMonth18(m18);
        p.setMonth24(m24);
        promotionPriceRepository.save(p);
    }
}