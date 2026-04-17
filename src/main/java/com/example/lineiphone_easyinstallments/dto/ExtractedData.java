package com.example.lineiphone_easyinstallments.dto;


public record ExtractedData(
        Integer age,
        String deviceModel,
        String capacity,   // 🌟 เพิ่มฟิลด์ความจุ
        String condition,
        Integer batteryHealth,
        String accessories,
        String repairHistory,
        String color
) {}