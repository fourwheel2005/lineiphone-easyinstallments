package com.example.lineiphone_easyinstallments.controller;

import com.example.lineiphone_easyinstallments.entity.UserState;
import com.example.lineiphone_easyinstallments.repository.UserStateRepository;
import com.example.lineiphone_easyinstallments.service.line.ChatFlowManager;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import com.linecorp.bot.messaging.model.ReplyMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import com.linecorp.bot.spring.boot.handler.annotation.EventMapping;
import com.linecorp.bot.spring.boot.handler.annotation.LineMessageHandler;
import com.linecorp.bot.webhook.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * คลาสนี้ทำหน้าที่เป็น "หู" และ "ปาก" คอยรับ Event จาก LINE และตอบกลับ
 * (รองรับโครงสร้างใหม่ของ LINE SDK v8+)
 */
@Slf4j
@RequiredArgsConstructor
@LineMessageHandler
public class LineWebhookController {

    private final ChatFlowManager chatFlowManager;
    private final MessagingApiClient messagingApiClient;
    private final UserStateRepository userStateRepository;

    @EventMapping
    public void handleTextMessageEvent(MessageEvent event) {

        if (event.message() instanceof TextMessageContent textMessageContent) {

            String userMessage = textMessageContent.text().trim();
            String replyToken = event.replyToken();
            String lineUserId = event.source().userId();


            if (event.source() instanceof com.linecorp.bot.webhook.model.GroupSource groupSource) {

                if (userMessage.equalsIgnoreCase("/groupid")) {
                    String groupId = groupSource.groupId();
                    log.info("🎯 มีการเรียกดู Group ID: {}", groupId);
                    messagingApiClient.replyMessage(new ReplyMessageRequest(
                            replyToken,
                            List.of(new TextMessage("Group ID ของกลุ่มนี้คือ:\n" + groupId)),
                            false
                    ));
                    return; // ตอบเสร็จแล้วหยุดทำงาน
                }

                log.info("🤫 ได้รับข้อความจากกลุ่มแอดมิน บอทจะไม่อ่านและไม่ตอบกลับครับ");
                return;

            } else if (event.source() instanceof com.linecorp.bot.webhook.model.RoomSource) {
                log.info("🤫 ได้รับข้อความจาก Room บอทจะไม่อ่านและไม่ตอบกลับครับ");
                return;
            }


            log.info("📩 ได้รับข้อความจากลูกค้า [{}]: {}", lineUserId, userMessage);

            try {
                String replyText = chatFlowManager.handleTextMessage(lineUserId, userMessage);

                if (replyText != null && !replyText.trim().isEmpty()) {
                    messagingApiClient.replyMessage(new ReplyMessageRequest(
                            replyToken,
                            List.of(new TextMessage(replyText)),
                            false
                    ));
                } else {
                    log.info("🤫 บอทเข้าโหมดเงียบ (ADMIN_MODE) จะไม่มีการตอบกลับอัตโนมัติ");
                }

            } catch (Exception e) {
                log.error("❌ เกิดข้อผิดพลาดในการประมวลผลข้อความ: ", e);
                messagingApiClient.replyMessage(new ReplyMessageRequest(
                        replyToken,
                        List.of(new TextMessage("ขออภัยครับ ระบบขัดข้องชั่วคราว รบกวนรอแอดมินสักครู่นะครับ 🛠️")),
                        false
                ));
            }
        }
    }

    @EventMapping
    public void handlePostbackEvent(PostbackEvent event) {

        String postbackData = event.postback().data();
        log.info("🎯 แอดมินกดปุ่ม Postback Data: {}", postbackData);

        try {
            Map<String, String> dataMap = parsePostbackData(postbackData);
            String action = dataMap.get("action");
            String serviceName = dataMap.get("service");
            String targetUserId = dataMap.get("userId"); // ID ของลูกค้าที่ต้องไปอัปเดตสถานะ

            if (targetUserId == null || action == null) return;

            String adminReplyMessage = "";
            String messageToCustomer = null;

            // 🌟 [จุดเพิ่มฟีเจอร์] กำหนดข้อความตอบแอดมิน และ ข้อความเด้งไปหาลูกค้า

            if (action.equals("approve") || action.equals("approve_doc") || action.equals("approve_credit")) {
                adminReplyMessage = "✅ เคสของลูกค้าอนุมัติผ่านเรียบร้อย! ระบบแจ้งลูกค้าแล้วครับ";
                messageToCustomer = "🎉 ยินดีด้วยครับ! ข้อมูลของคุณได้รับการอนุมัติเรียบร้อยแล้ว แอดมินจะรีบดำเนินการขั้นตอนต่อไปให้นะครับ";
                UserState state = userStateRepository.findByLineUserId(targetUserId).orElse(null);
                if (state != null) {
                    if ("balloon".equals(serviceName)) {
                        state.setCurrentState("STEP_5_PRICING");
                        userStateRepository.save(state);

                        String nextStepMessage = chatFlowManager.handleTextMessage(targetUserId, "continue");
                        if (nextStepMessage != null) {
                            messageToCustomer += nextStepMessage;
                        }
                    }
                }
            } else if (action.equals("reject") || action.equals("reject_credit")) {
                adminReplyMessage = "❌ เคสนี้ถูกปฏิเสธเรียบร้อยครับ";
                messageToCustomer = "ต้องขออภัยด้วยนะครับ 🙏 จากการตรวจสอบข้อมูล ยังไม่ผ่านเกณฑ์การพิจารณาในครั้งนี้ครับ หากมีข้อสงสัยสามารถพิมพ์สอบถามแอดมินได้เลยครับ";
            } else if (action.equals("take_case")) {
                adminReplyMessage = "💬 รับเรื่องแล้ว! คุยกับลูกค้าต่อได้เลยครับ";
                messageToCustomer = "แอดมินตัวจริงมารับเรื่องแล้วครับ! พิมพ์สอบถามได้เลยครับ 👇";
            } else if (action.equals("resume_bot")) {
                adminReplyMessage = "▶️ เปิดบอทให้ดูแลลูกค้าคนนี้ต่อแล้วครับ";
                // คุณอาจจะต้องเรียก chatFlowManager เพื่ออัปเดต State ให้หลุดจาก ADMIN_MODE ด้วยที่นี่
            }

            // 1. ตอบกลับแอดมินที่กดปุ่ม (Reply)
            messagingApiClient.replyMessage(new ReplyMessageRequest(
                    event.replyToken(),
                    List.of(new TextMessage(adminReplyMessage)),
                    false
            ));


            if (messageToCustomer != null) {
                List<Message> pushMessages = List.of(new TextMessage(messageToCustomer));

                messagingApiClient.pushMessage(
                        null,
                        new PushMessageRequest(
                                targetUserId,
                                pushMessages,
                                false,
                                (List<String>) null
                        )
                );
            }

        } catch (Exception e) {
            log.error("❌ Error processing postback: ", e);
        }
    }

    private Map<String, String> parsePostbackData(String data) {
        Map<String, String> map = new HashMap<>();
        if (data == null || data.isEmpty()) return map;

        String[] pairs = data.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }
        return map;
    }
}