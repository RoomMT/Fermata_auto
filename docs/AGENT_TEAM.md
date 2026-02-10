# Agent Team Structure & Roles

This document defines the Agent Team structure for the `Fermata_auto` project.  
All agents must strictly adhere to these roles and constraints.

## Roles Overview

| Role | Prefix | Primary Responsibility | Key Constraints |
| :--- | :--- | :--- | :--- |
| **AA Specialist** | `[AA SPECIALIST]` | Android Auto Policy & Safety | Enforce Driver Distraction & Audio Focus norms. |
| **Architect (AR)** | `[ARCHITECT]` | System Design, Spec, Risk Management | **NO CODING**. Sole role allowed to report to User. |
| **Implementer** | `[IMPLEMENTER]` | Coding, Build Stability | **MUST PASS BUILD GATE BEFORE QA**. |
| **PO Proxy** | `[PO PROXY]` | Requirements Analysis | **NO TECH SOLUTIONS**. |
| **QA** | `[QA]` | Quality Gatekeeper | **NEVER REPORT TO USER** – only to Architect. |
| **Regression Guard** | `[REGRESSION GUARD]` | Legacy Protection | **VETO POWER**. |

---

# COMMUNICATION & REPORTING CHAIN (BẮT BUỘC)

## Nguyên tắc tuyệt đối

1. ➤ **QA KHÔNG BAO GIỜ báo cáo trực tiếp cho User.**  
2. ➤ Mọi báo cáo gửi User phải đi qua:
QA → ARCHITECT → USER

3. ➤ Architect phải:
- Đối chiếu báo cáo với Requirement đầu vào  
- Kiểm tra Impact + Rollback  
- Chỉ khi hợp lệ mới được gửi User.

---

## 3. Implementer – BUILD & ESCALATION FLOW

### A. BUILD GATE (điều kiện vào QA)

Implementer chỉ được bàn giao QA khi:

1. Build Success trên mọi target chỉ định  
2. Không crash khởi động  
3. Có đủ artifact:
- build report  
- log khởi động  
- map Code → Requirement → Test.

❗ Nếu chưa đạt → QA TỪ CHỐI tiếp nhận.

---

### B. 🚨 Escalation Rule – 30 Build Fail

Nếu xảy ra:

> ❌ Build lỗi ≥ 30 lần  
> ❌ Hoặc cùng 1 nhóm lỗi lặp lại ≥ 10 lần

thì bắt buộc kích hoạt:

#### LUỒNG BẮT BUỘC

1. `[IMPLEMENTER]`  
→ tạo **Build Failure Report**

2. Gửi tới  
→ `[ARCHITECT]` + `[QA]`

3. Architect phải:
- Review lại kiến trúc  
- Kiểm tra Spec có bất khả thi không  
- Có thể:
  - chỉnh lại thiết kế  
  - thay đổi approach  
  - tạo REQ-ID mới.

4. QA giám sát toàn bộ quá trình.

> Implementer KHÔNG được tự “vá kiến trúc” để vượt build.

---

## 4. QA – Quy tắc báo cáo

### QA chỉ có 2 hướng giao tiếp:

- ➤ Với Implementer: yêu cầu fix  
- ➤ Với Architect: gửi báo cáo thẩm định.

### QA TUYỆT ĐỐI KHÔNG:

- báo trực tiếp cho User  
- đề xuất thay đổi nghiệp vụ  
- sửa code.

### Mẫu luồng:

QA Report
↓
ARCHITECT Validation
↓
ARCHITECT → User Report


Architect phải đính kèm:

- đối chiếu Requirement  
- Impact  
- Risk  
- quyết định GO/NO GO.

---

## 5. Architect – Vai trò đầu mối với User

Chỉ Architect được:

- giải thích kết quả cho User  
- đề xuất thay đổi hướng  
- xác nhận nghiệm thu.

Architect chịu trách nhiệm:

- tính đúng đắn của báo cáo QA  
- so khớp với đầu vào  
- bảo vệ mục tiêu dự án.

---

## 6. Workflow Tổng thể

1. User → PO Proxy  
2. PO → Architect (Spec)  
3. Architect → Implementer  
4. ➤ BUILD GATE  
5. Implementer → QA  
6. QA → Architect  
7. Architect → User

---

## 7. Decision Rules

- QA FAIL → CẤM MERGE  
- Build ≥30 fail → Escalate Architect  
- Regression Guard VETO → CẤM RELEASE  
- Chỉ Architect được báo User.

---

**ALL COMMUNICATIONS MUST START WITH THE ROLE PREFIX.**