package com.example.lineiphone_easyinstallments.service.line;

import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
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

    @Transactional
    public String handleTextMessage(String lineUserId, String userMessage) {
        String msg = userMessage.trim();


        if (msg.equalsIgnoreCase("/pause")) {
            UserState userState = userStateRepository.findByLineUserId(lineUserId).orElse(new UserState());
            userState.setCurrentState("ADMIN_MODE");
            userStateRepository.save(userState);
            return null;
        }

        if (msg.equalsIgnoreCase("เริ่มใหม่") || msg.equalsIgnoreCase("เมนูหลัก")) {
            return resetUserState(lineUserId);
        }

        UserState userState = userStateRepository.findByLineUserId(lineUserId).orElse(new UserState());
        if ("ADMIN_MODE".equals(userState.getCurrentState())) {
            return null;
        }

        if (msg.equalsIgnoreCase("เริ่มใหม่") || msg.equalsIgnoreCase("เมนูหลัก")) {
            return resetUserState(lineUserId);
        }

        UserState userState2 = userStateRepository.findByLineUserId(lineUserId)
                .orElseGet(() -> {
                    UserState newState = new UserState();
                    newState.setLineUserId(lineUserId);
                    newState.setCurrentState("INIT");
                    return userStateRepository.save(newState);
                });

        log.info("User {} is at state: {} | message: {}", lineUserId, userState.getCurrentState(), msg);


        for (ServiceFlowHandler handler : flowHandlers) {
            if (handler.supports(msg)) {
                log.info("🚀 User {} triggered Rich Menu -> Jumping to flow: {}", lineUserId, msg);

                userState.setSelectedService(msg);


                userState.setCurrentState("STEP_1_SCREENING");
                userStateRepository.save(userState);

                return handler.processMessage(userState, "");
            }
        }


        String selectedService = userState.getSelectedService();
        if (selectedService != null) {
            for (ServiceFlowHandler handler : flowHandlers) {
                if (handler.supports(selectedService)) {
                    return handler.processMessage(userState, msg);
                }
            }
        }

        return "สวัสดีครับ 🙏 ยินดีต้อนรับสู่ร้าน iPhone Easy Installments\n\n" +
                "สามารถกดเลือกบริการจากเมนูด้านล่าง (Rich Menu) เพื่อเริ่มต้นทำรายการได้เลยครับ 👇\n\n" +
                "📱 ผ่อนบอลลูน\n" +
                "💰 ออมดาวน์\n" +
                "✨ ไอโฟนผ่อนง่าย\n" +
                "♻️ รับซื้อไอโฟน";
    }


    private String resetUserState(String lineUserId) {
        userStateRepository.findByLineUserId(lineUserId).ifPresent(state -> {
            state.setCurrentState("INIT");
            state.setSelectedService(null);
            userStateRepository.save(state);
        });
        return "ระบบรีเซ็ตการทำรายการเรียบร้อยครับ 🔄\n" +
                "สามารถกดเลือกบริการใหม่จากเมนูด้านล่าง (Rich Menu) ได้เลยครับ 👇";
    }
}