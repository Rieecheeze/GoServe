# Seryo conversation and plus menu — UI revision

Date: 2026-10-15. Scope: Figma UI only, before Android implementation.

## Design direction

Use the user's ChatGPT screenshots for conversation structure and plus-menu placement. Retain GoServe's Montserrat/Karla typography, monochrome Light/Dark tokens, logo and six-destination navigation. The example screenshot's RutaRescue text is reference content, not GoServe requirements.

Energy 1 / Rhythm 1 / Motion 1: campus-service guidance needs calm, readable exchanges and predictable controls. Right-aligned student bubbles identify the speaker; unboxed Seryo replies make longer guidance readable. The composer stays below the scrollable thread. A single elevated menu groups deliberate actions near the plus button.

## Agreed Seryo scope

- Campus office/service/request guidance grounded in approved knowledge.
- Explanation of the consenting user's current, non-sensitive temporary document.
- One PDF, JPG or PNG, no more than 10 MB, validated before provider processing.
- Current-session conversation only, without persistent chat or upload history.
- Contextual office actions inside Seryo replies open the requested native office directly.
- The plus menu contains only Attach a file and AI privacy. Office inquiries remain a separate native flow.

The interface can suggest a next step and open the relevant native flow. App features do not automatically become authorized AI tools. Agent procedures and the eGovAI request/response contract remain pending; Send, actual file selection/upload and autonomous actions are not enabled by this design work.

## Plus menu

| Action | Purpose | Existing destination |
|---|---|---|
| Attach a file | Review temporary-analysis rules and consent before explicit file selection | AI upload/setup panel |
| AI privacy | Review optional AI processing and data use | Seryo privacy |

Menu Close and the backdrop return to the exact empty/conversation origin. Browse offices and Ask an office are not plus-menu entries. Opening an office or privacy/setup panel does not send a message, submit a request, book a consultation, declare a fee, mark a payment, release a file or change a record.

## Conversation example

Use three student bubbles and three plain Seryo replies:

| Speaker | Example copy | Contextual action |
|---|---|---|
| Student | Which office handles payments? | — |
| Seryo | Cashiering handles payments when the office declares a fee for your request. | Open Cashiering |
| Student | Does every request need payment? | — |
| Seryo | No. The office declares whether a fee is required. A request declared free skips payment. | — |
| Student | Take me to Registrar. | — |
| Seryo | Open Registrar to review its listed services and availability. | Open Registrar |

Payment intent offers Open Cashiering inside the relevant reply. An explicit office intent offers one action that opens that exact office in one tap; it does not detour through the directory or request another confirmation merely to view the page. Native inquiries remain available through their existing flow rather than the AI plus menu.

These facts come from the existing GoServe plan and user instructions. The fixture is not a provider-generated answer or an institutional service decision. Actual answers must carry appropriate approved-source grounding and remain guidance.

### Contextual office destinations

| Requested office | Action label | Light frame | Dark frame |
|---|---|---|---|
| Cashiering / payment intent | Open Cashiering | `60:2` | `60:246` |
| Registrar | Open Registrar | `19:175` | `36:173` |
| Student Accounting | Open Student Accounting | `60:70` | `60:314` |
| Guidance and Counseling Center | Open Guidance | `60:104` | `60:348` |
| College of Computer Studies | Open CCS | `60:138` | `60:382` |

These are native office-page handoffs. Open Cashiering does not start checkout, create a transaction or confirm a payment. The other office actions likewise perform navigation only.

## Layout checks

- Match both Light and Dark; use 48-unit minimum interactive targets.
- Preserve the selected AI Chat navigation item and its existing routes.
- Thread scrolls independently of the bottom composer. User bubbles wrap and stay within the available width.
- The menu stays within the available phone area; long menu content scrolls.
- Verify the changed layouts at 390×844, 320×720 and simulated 2× text where applicable.
- Existing consent, invalid-file, provider-unavailable and cleanup states remain part of the review. No typing indicator, retained history or unrelated general-purpose tools are added.

## Evidence boundary

Figma can demonstrate menu opening, closing and native-screen handoffs. It cannot prove Android IME behavior, API calls, file processing, consent persistence, ownership enforcement, approved answers or verified cleanup. These remain implementation and provider-contract checks after UI approval.

## Source references

| Source | Declared rule | UI consequence |
|---|---|---|
| [Project plan](GoServe-project-plan-v2.2.md), FR-17 and AT-31/32 | Answers use approved knowledge and are labeled guidance; unsupported questions go to the relevant office. | Plain Seryo replies may explain approved project facts and include the relevant native office action. Office actions belong in contextual replies, not the plus menu. No invented institutional answer. |
| [Project plan](GoServe-project-plan-v2.2.md), FR-18 and AT-33/34 | After separate consent, accept one non-sensitive PDF, JPG or PNG, at or below 10 MB; block invalid count/type/size before provider processing. | Attach a file opens the existing rules/consent flow. It does not select or upload a file automatically. |
| [Project plan](GoServe-project-plan-v2.2.md), FR-09 and Section 4 | Cashiering handles payments; fees require an office declaration; free requests skip payment. | These fixed project facts ground the conversation fixture. The reply cannot declare a fee or confirm payment. |
| [Project plan](GoServe-project-plan-v2.2.md), Section 3.1 and FR-21 | Students access their own records; staff stay within assigned authority; tellers confirm cash. Cancellation/rejection requires a reason. | A handoff preserves the signed-in user's role and record context. It grants no additional access. |
| [eGovAI contract](../backend/egovai-contract.md), lines 9–23 | Procedures and provider transport remain disabled until supplied and validated. Trusted server operations enforce permissions; temporary cleanup must be verified on success and failure. | The agent-style interface enables no provider call, autonomous write or simulated cleanup result. |

## Confirmation and authorization boundaries

- **Agent guidance means a native handoff.** A contextual Open office action opens that exact native page directly. Inquiries remain a separate native flow, with explicit Send. Any later request, consultation or payment operation retains its native review and explicit user action; no record changes on a reply, office-page action or menu selection.
- **Temporary analysis needs its own consent.** App registration agreement does not authorize AI processing. The user explicitly chooses a file, reviews it and submits it only after validation; current setup keeps selection, upload and Send disabled.
- **Authority remains server-controlled.** Students act on their own records; staff require the relevant office/capability; only an authorized teller or verified provider reconciliation can confirm payment. Private-file release also requires ownership, request and applicable payment gates.
- **Cancellation/rejection keeps its reason and confirmation step.** Appointment changes keep their native confirmation. Paid records remain paid, with manual Cashiering follow-up and no automatic refund promise.
- **No silent completion.** AI text cannot declare fees, mark a request or payment complete, release a file, or claim that a temporary file was deleted. A confirmed result requires the corresponding authorized server response. Approved Seryo procedures and eGovAI contracts remain prerequisites for later implementation.

