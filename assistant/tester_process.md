# Tester Process & Workflow

**⚠️ ข้อบังคับสำคัญ (Mandatory Prerequisite):**
ก่อนที่จะเริ่มออกแบบ Test Case, เขียน Test Script หรือทำการทดสอบระบบทุกครั้ง **ต้องกลับไปอ่านไฟล์ `assistant/coder.md` และ `assistant/tester.md`** เพื่อประสานความเข้าใจร่วมกับทีม Development และยึดมั่นในมาตรฐานการทดสอบสูงสุดเสมอ

**กระบวนการทำงาน (Workflow):**

1. **Requirement Analysis:** วิเคราะห์ requirement และ Acceptance Criteria ให้ครบถ้วน เพื่อหาช่องโหว่ทาง business logic
2. **Test Planning & Design:** 
   - วางแผนกลยุทธ์การทดสอบ (Test Plan)
   - ออกแบบ Test Case ให้ครอบคลุมทุก Scenarios (Happy, Negative, Edge cases)
3. **Test Execution:**
   - รัน Manual Test สำหรับฟีเจอร์ใหม่ให้มั่นใจ
   - รัน Automated Test (Regression) เพื่อรับประกันว่าของเก่าไม่พัง
4. **Bug Reporting & Tracking:** 
   - เปิด issue แจ้งบัคพร้อมรายละเอียดที่ครบถ้วน (Steps, Expected/Actual result, Environment)
   - ติดตามผลการแก้ไขบัคและทำการ Re-test ทันทีเมื่อ Coder แก้ไขเสร็จ
5. **บันทึกการทำงาน (Audit Trail / Changelog):**
   - **ทุกครั้งที่มีการรันเทสต์, สร้าง Test Case, รีวิว requirement หรือเปิด/ปิดบัค จะต้องทำการบันทึกการทำงานด้านล่างนี้เสมอ**
   - รูปแบบการบันทึก: `- [YYYY-MM-DD HH:mm:ss] - [กิจกรรมที่ทำ / Test Case ID / Bug ID] - [ผลลัพธ์ / สถานะ]`

---

## 📝 Tester Activity Log
*(บันทึกทุกการกระทำ หรือการทดสอบที่นี่เรียงตามลำดับเวลา)*

- [2026-05-25 22:31:00] - [Review System Vulnerabilities] - [วิเคราะห์ช่องโหว่ (Data Truncation, Thread Leak, ReDoS, Empty String) และแจ้ง Coder ให้ดำเนินการแก้ไขเรียบร้อยแล้ว]
- [2026-05-25 22:39:00] - [Review System UX/Infinite Loop] - [วิเคราะห์ช่องโหว่การติดลูป และแนะนำให้สร้าง FlowRetryManager พร้อมตรวจสอบกรณีลูกค้ายังไม่ได้กดแผนกใดๆ]
- [2026-05-25 22:42:00] - [Review Response Phrasing] - [ตรวจสอบคำว่า "แอดมินตัวจริง" ทั่วทั้งระบบ และสั่งให้ Coder ลบออกเพื่อป้องกันลูกค้ารู้สึกถึงความไม่เป็นมืออาชีพ (Scammer-like phrasing)]
- [2026-05-25 22:48:00] - [Review Prompts for Accuracy/Naturalness] - [ตรวจสอบ Prompt พบ Critical Bug กรณีตีความหมาย 'อายุ' ทับซ้อนกับ 'รุ่นโทรศัพท์' จึงสั่งให้ Coder เพิ่ม Context-Awareness เพื่อแก้ไขบัค พร้อมเพิ่มเงื่อนไขแสลงภาษาไทย]
