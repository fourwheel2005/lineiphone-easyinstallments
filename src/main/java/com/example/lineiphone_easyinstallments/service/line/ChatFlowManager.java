package com.example.lineiphone_easyinstallments.service.line;

import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.ai.AiChatService;
import com.example.lineiphone_easyinstallments.service.flow.ServiceFlowHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFlowManager {

    private final UserStateRepository userStateRepository;
    private final List<ServiceFlowHandler> flowHandlers;

    // 🌟 ดึงสมอง AI เข้ามาเสียบเป็นด่านสุดท้าย
    private final AiChatService aiChatService;

    @Transactional
    public String handleTextMessage(String lineUserId, String userMessage) {
        String msg = userMessage.trim();

        // 🌟 1. Single DB Hit: ดึง State หรือสร้างใหม่ (ดึงแค่ครั้งเดียวจบ ประหยัดทรัพยากร)
        UserState userState = userStateRepository.findByLineUserId(lineUserId)
                .orElseGet(() -> {
                    UserState newState = new UserState();
                    newState.setLineUserId(lineUserId);
                    newState.setCurrentState("INIT");
                    return userStateRepository.save(newState);
                });

        log.info("User {} is at state: {} | message: {}", lineUserId, userState.getCurrentState(), msg);

        // 🌟 2. Guard Clauses (ดักคำสั่งพิเศษก่อนทำอย่างอื่น)

        // แอดมินสั่งปิดบอท
        if (msg.equalsIgnoreCase("/pause")) {
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);
            return null; // บอทเงียบ
        }

        // ลูกค้ากดรีเซ็ต / เริ่มใหม่
        if (msg.equalsIgnoreCase("เริ่มใหม่") || msg.equalsIgnoreCase("เมนูหลัก")) {
            return resetUserState(userState);
        }

        // ถ้าอยู่ในโหมดแอดมิน ให้บอทหลับยาวๆ ไปเลย
        if ("ADMIN_MODE".equals(userState.getCurrentState())) {
            return null;
        }

        // 🌟 3. ตรวจสอบว่าลูกค้ากดปุ่ม Rich Menu เพื่อเริ่ม Flow ใหม่หรือไม่
        for (ServiceFlowHandler handler : flowHandlers) {
            if (handler.supports(msg)) {
                log.info("🚀 User {} triggered Rich Menu -> Jumping to flow: {}", lineUserId, msg);

                // เปลี่ยนแผนกให้ลูกค้า
                userState.setSelectedService(handler.getServiceName());
                // ล้าง State เดิมทิ้ง (เคลียร์เป็น null เพื่อให้ Handler นั้นๆ เริ่มรันเคส default ของตัวเอง)
                userState.setCurrentState(null);
                userStateRepository.save(userState);

                // ส่งเข้า Flow นั้นๆ โดยส่งข้อความว่างๆ ("") ไปเพื่อกระตุ้นให้ Flow พ่นคำถามแรกออกมา
                return handler.processMessage(userState, "");
            }
        }

        // 🌟 4. ตรวจสอบว่าลูกค้ากำลังอยู่ใน Flow (แผนก) ใดแผนกหนึ่งอยู่หรือไม่
        String selectedService = userState.getSelectedService();
        if (selectedService != null && !selectedService.isEmpty() && !"INIT".equals(userState.getCurrentState())) {
            for (ServiceFlowHandler handler : flowHandlers) {
                if (handler.supports(selectedService)) {
                    // ส่งข้อความให้แผนกนั้นจัดการต่อ
                    return handler.processMessage(userState, msg);
                }
            }
        }


        log.info("🤖 Routing message to AI for User: {}", lineUserId);
        return aiChatService.generateResponse(lineUserId, msg);
    }


    private String resetUserState(UserState state) {
        state.setCurrentState("INIT");
        state.setSelectedService(null);
        state.setDeviceModel(null);
        state.setPreviousState(null);
        userStateRepository.save(state);

        return "ระบบรีเซ็ตการทำรายการเรียบร้อยครับ 🔄\n" +
                "สามารถกดเลือกบริการใหม่จากเมนูด้านล่าง (Rich Menu) ได้เลยครับ 👇";
    }
}