package com.example.lineiphone_easyinstallments.service.flow;


import com.example.lineiphone_easyinstallments.entity.UserState;

public interface ServiceFlowHandler {

    // ตรวจสอบว่าคลาสนี้รับผิดชอบบริการชื่อนี้ใช่หรือไม่ (เช่น "ผ่อนบอลลูน")
    boolean supports(String serviceName);

    // ดึงชื่อบริการเพื่อนำไปใช้แสดงผล
    String getServiceName();

    // ฟังก์ชันหลักที่แต่ละบริการจะเอาไปเขียน Logic ของตัวเอง (State Machine)
    String processMessage(UserState userState, String userMessage);
}
