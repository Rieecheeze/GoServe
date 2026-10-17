# Seryo inline attachments — Figma handoff

Updated: 2026-10-16. **Built in Figma; awaiting UI approval.** Android implementation and live provider operations remain on hold.

## Current design

Seryo accepts a photo or document **inside the chat**. The separate **Temporary file analysis / AI Upload page has been removed**. Its existing frame IDs now show an inline image-preview chat state.

The plus menu has exactly **Attach a file** and **AI privacy**. Accept **one PDF, JPG or PNG up to 10 MB**. Explain and Extract text prepare an unsent question using the current attachment. No camera capture, bulk analysis, saved chat history or uploaded-file gallery is included.

**Allow opens an empty allowed chat.** It never selects a file, opens the picker or sends a question automatically. **Decline opens an AI-off chat**, with ordinary app navigation available. Review AI privacy permits a later change. AI consent is separate from the registration agreement.

The Figma picker, busy, result and cleanup screens are review fixtures. They do not read a device file, call eGovAI, record consent, process a file or verify deletion. **Send remains disabled** while the provider contract and approved Seryo procedures are pending.

## Built panel inventory

[Figma file](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno) · page `14:2` · [inline flow review panel](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=289-9080)

**19 cases / 38 Light and Dark phone frames, plus two transparent tools overlays.** The review panel has 40 links. The existing empty chat, conversation and previous menu-origin examples are outside this count.

| Case | Light | Dark | Main coverage |
|---|---|---|---|
| Seryo privacy | `95:2` | `97:162` | Allow → allowed empty chat; Decline → AI off; Back → origin |
| G12.1 Allowed empty chat | `145:1726` | `145:1728` | No file selected; privacy can be reviewed |
| G12.2 AI off | `145:1730` | `145:1732` | Reconsider privacy; ordinary app navigation stays available |
| G12.3 Inline PDF preview | `145:1734` | `145:1736` | One composer attachment; Explain, Extract text, Remove, replace |
| G12.4 Invalid attachment | `145:1738` | `145:1740` | Supported types/count/size; choose again or remove |
| G12.5 Working | `145:1742` | `145:1744` | Preserve question and attachment; duplicate processing blocked |
| G12.6 Unsupported request | `145:1746` | `145:1748` | Scope refusal; return to chat |
| G12.7 Rate limited | `145:1750` | `145:1752` | Return to chat; no invented retry time or automatic loop |
| G12.8 Cleanup unconfirmed | `145:1754` | `145:1756` | Further processing blocked until server verification |
| G12.9 Cleanup verified fixture | `145:1758` | `145:1760` | Text conversation remains; no original-file history |
| Inline image preview, replacing AI Upload | `24:82` | `36:850` | Same composer and operations as the PDF preview |
| Retryable inline error | `24:111` | `36:879` | Retry returns to an unsent operation draft |
| G12.picker | `274:8360` | `274:8367` | Explicit image/PDF fixture selection or Cancel |
| G12.picker-cancelled | `274:8361` | `274:8368` | No file sent; unsent draft retained |
| G12.explain-draft | `274:8362` | `274:8369` | Composer: “Explain this file.”; Send disabled |
| G12.extract-draft | `274:8363` | `274:8370` | Composer: “Extract text from this file.”; Send disabled |
| G12.explain-result | `274:8364` | `274:8371` | Bounded explanation of the supplied Settings screenshot |
| G12.extract-result | `274:8365` | `274:8372` | Exact two-line Settings text fixture |
| G12.no-readable-text | `274:8366` | `274:8373` | Clearer image / another file; no fabricated extraction |
| Tools overlay, outside 19-case count | `274:8374` | `274:8375` | Attach → picker after consent; privacy → privacy panel; backdrop/Close dismiss |

The overlays are transparent full-phone frames with their own dim backdrop. They use `OVERLAY` navigation and `CLOSE` actions, retaining the underlying chat. Initial unconsented menu entry goes to privacy; a consented inline-chat overlay goes to the picker.

## Return and processing rules

- File selection does not trigger analysis. Explain/Extract choose the draft; the user must explicitly Send once a real provider integration is available.
- A valid image or PDF occupies the same single attachment slot. Replace must not add a second file. Invalid selection preserves the existing valid attachment and unsent draft.
- Picker cancellation preserves the actual current thread, draft, selected operation and previous attachment. The empty-origin fixture shows “No file selected.”
- Remove clears the attachment and its operation while retaining unrelated typed text and the thread. Switching Explain/Extract retains the same file.
- Retry restores the matching operation draft and context. It must not silently resend, bypass validation or clear an unresolved-cleanup block.
- Busy, error, results and cleanup outcomes are isolated review entries. No timeout leads to a fabricated result or deletion success. Back does not imply cancellation of a running provider operation.
- Declining blocks new AI processing. It must not promise that an already-running operation was cancelled. Any temporary file still needs verified cleanup.
- Runtime returns must retain the actual ongoing conversation. Fixture navigation to the empty-chat frame does not implement native state preservation.

### Result fixture source

Only the supplied Settings screenshot is used:

> Settings  
> Light and dark appearance

The explanation is “The image shows a Settings heading and a Light and dark appearance option.” Extract text preserves the two source lines. The PDF fixture illustrates the same text; it does not claim an actual PDF was selected. No file size, student content, provider metadata or confidence score is invented. Off-canvas annotations identify fixtures; results identify their supplied screenshot source.

## Layout, depth and completed checks

- Chat content uses a clipped vertical scroll viewport above a pinned composer and bottom navigation. Long privacy, picker and result content can scroll. Tools have a bounded scroll viewport. Compact and enlarged-text proofs cover the new flow.
- The scoped acceptance check passed **38 flow frames + two overlays with zero issues**. Geometry passed **46 roots**, including eight compact/enlarged-text proofs, with zero issues.
- The effect pass applied depth to **350 surfaces**, preserving geometry. Soft shadows distinguish raised cards, composer, menus and navigation; plain Seryo replies remain unboxed. Montserrat/Karla, the monochrome identity and Light/Dark contrast are retained.
- The final scroll audit passed **468 phone/proof frames**, with all **468 bounded viewports** enabled and **107 long-content bodies** reachable. No unsafe layouts or geometry changes remained. The canvas audit preserved **470 mobile/proof/overlay frames**, checked **859 tracked source objects** and **2,990 links within those frames**, and found no missing destinations, section overflow or section overlap.
- Shared field and primary-action families now provide **16 static Light/Dark variants** for rest, focus, invalid input, press and disabled styling: component sets `294:9166` and `294:9201`. Native focus and touch behavior are implementation work.

These checks prove the inspected Figma structure, visible copy, prototype routes and geometry. They do not prove native keyboard behavior, device accessibility, real file selection, provider responses or backend cleanup.

## Contextual office navigation

Office inquiries stay separate from the plus menu. Relevant replies may offer **Open Cashiering** for payment intent or a direct button to the requested office.

| Office | Light | Dark |
|---|---|---|
| Cashiering | `60:2` | `60:246` |
| Registrar | `19:175` | `36:173` |
| Student Accounting | `60:70` | `60:314` |
| Guidance | `60:104` | `60:348` |
| CCS | `60:138` | `60:382` |

These buttons navigate only. They do not send inquiries, submit requests, declare fees, start checkout or mark payments paid. Attachment contents are data to explain/extract, not permission to expand Seryo's tools or access another user's records.

## Native implementation checklist — after UI approval

- Implement account-scoped optional AI consent, reconsideration and session restoration; keep registration consent separate.
- Use the real system picker and validate one file, allowed MIME/content types and the 10 MB limit. Test cancellation, replacement, corrupt files and inaccessible URIs.
- Preserve thread, unsent text, operation and attachment across picker/privacy returns, rotation and lifecycle changes. Prevent duplicate sends.
- Recheck consent, session, ownership, provider readiness and cleanup state before processing. Enable Send only when these checks pass.
- Confirm eGovAI request/response contracts and approved agent procedures before enabling transport. Keep credentials server-side and enforce authorized tool scope.
- Test busy, timeout, offline, authentication failure, rate limit, unreadable text and retry paths against real native/provider outcomes. Do not substitute fixtures for responses.
- Verify temporary storage ownership, expiry and deletion on the server. Unconfirmed cleanup blocks further processing; show “removed” only after evidence. Do not retain original-file/chat history.
- Test scrolling, keyboard/IME clearance, TalkBack, focus, pressed states, 48-unit targets, large text and Light/Dark contrast on actual devices. Confirm office buttons open the correct native destination.

UI coverage reduces missing-design decisions; implementation and device/provider testing can still reveal issues. Android coding starts only after the user finalizes the UI.

Sources: [project plan FR-17/18, AT-31–34 and Section 9.3](GoServe-project-plan-v2.2.md), [eGovAI integration contract](../backend/egovai-contract.md), [conversation design](GoServe-seryo-conversation-ui.md), and the user's current Explain/Extract, consent, scrolling and depth requirements.


