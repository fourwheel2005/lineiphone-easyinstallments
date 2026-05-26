# Coder Process & Workflow

**⚠️ ข้อบังคับสำคัญ (Mandatory Prerequisite):**
ก่อนที่จะเริ่มเขียนโค้ด หรือทำการรีวิวโค้ดทุกครั้ง **ต้องกลับไปอ่านไฟล์ `assistant/coder.md` และ `assistant/tester.md`** เพื่อเตือนความจำถึงบทบาท มาตรฐานที่คาดหวัง และแนวทางปฏิบัติให้ครบถ้วนเสมอ

**กระบวนการทำงาน (Workflow):**

1. **Understand Requirements:** ทำความเข้าใจ requirement และ business logic อย่างถ่องแท้ก่อนเริ่มเขียนโค้ด
2. **Design & Plan:** วางแผนโครงสร้างโค้ดและ Design Pattern ที่จะใช้ รวมถึงคิดเผื่อกรณี error ต่างๆ
3. **Implementation:** 
   - เขียนโค้ดตามมาตรฐานที่กำหนดใน `coder.md`
   - เขียน Unit Test ควบคู่ไปกับการเขียนโค้ดให้ครอบคลุม
4. **Code Review:** ตรวจสอบโค้ดของตัวเอง (Self-review) ก่อนเสมอ จากนั้นทำการรีวิวโค้ดของผู้อื่นด้วยความละเอียดรอบคอบ และให้ feedback อย่างมืออาชีพ
5. **บันทึกการทำงาน (Audit Trail / Changelog):**
   - **ทุกครั้งที่มีการแก้ไขไฟล์ เพิ่มลบฟีเจอร์ หรือกระทำการใดๆ ในโปรเจกต์ จะต้องทำการบันทึกลงใน Log ด้านล่างนี้เสมอ** เพื่อให้สามารถตรวจสอบย้อนหลังได้
   - รูปแบบการบันทึก: `- [YYYY-MM-DD HH:mm:ss] - [ชื่อไฟล์ที่แก้ / Action] - [รายละเอียดสิ่งที่ทำและเหตุผล]`

---

## 📝 Coder Activity Log
*(บันทึกทุกการกระทำ หรือการแก้ไขโค้ดที่นี่เรียงตามลำดับเวลา)*

- [2026-05-25 22:31:00] - [LineWebhookController.java / BalloonFlowService.java] - [เพิ่ม Input Validation เช็คความยาวข้อความ > 500, เปลี่ยน Thread เป็น ScheduledExecutorService เพื่อแก้ Thread Leak, และเปลี่ยน Regex .* เป็น .contains() เพื่อกัน ReDoS]
- [2026-05-25 22:39:00] - [FlowRetryManager.java / ChatFlowManager.java] - [สร้าง FlowRetryManager รวมศูนย์จัดการ Retry, ย้ายระบบดักจับคำว่าแอดมินมาไว้ที่ ChatFlowManager และเชื่อม Group ID ตามแผนก]
- [2026-05-25 22:42:00] - [All Flow Services & Controllers] - [ลบคำว่า "แอดมินตัวจริง" ออกจากข้อความตอบกลับของบอททั้งหมด เพื่อให้ภาษาดูเป็นธรรมชาติและไม่เหมือนมิจฉาชีพ]
- [2026-05-25 22:48:00] - [Prompt Engineering] - [ปรับปรุง Prompt 3 ตัว: แก้ Age Bug โดยเพิ่ม Context-Awareness ใน Extractor, เพิ่มแสลงใน Screening, และปรับ Tone of Voice ใน Base ให้เป็นธรรมชาติขึ้น]
- [2026-05-26 21:35:00] - [TradeInFlowService.java] - [เพิ่มการส่งรูปภาพตัวอย่างการเช็คแบตเตอรี่ในขั้นตอนถามสุขภาพแบตเตอรี่ (STEP_3_BATTERY) พร้อมกำหนดตัวแปร EXAMPLE_BATTERY_IMG_URL รอใส่ลิ้งก์จริง]
- [2026-05-26 21:55:00] - [Prompt Engineering] - [เพิ่มเงื่อนไขใน screening-prompt.st หากถามเรื่องติดผ่อน/iCloud แล้วตอบ "ปกติ" ให้แปลว่า "NO" เพื่อแก้ปัญหาบอทไม่เข้าใจบริบท]
