# GoServe Privacy Notice

**Review draft 1.0 | Prepared 21 October 2026 | Not yet effective**

**Publication is pending the decisions identified below.** This notice describes the intended native Android service and its information needs. It must be checked against the enabled app and actual provider arrangements before release. It does not mean that every described feature is already available or that real student records have been approved for the demonstration.

## 1. Who is responsible for your information

The named Operator for this demonstration is **GoServe**. The listed contact for app support, correction requests and privacy concerns is **GoServeSupport@GoServe.Test**. The responsible person or legal entity and address remain **[LEGAL IDENTITY AND ADDRESS]**; naming the app does not establish a separate legal entity or appoint a Data Protection Officer.

**Demonstration contact:** the [reserved `.test` domain](https://www.iana.org/assignments/special-use-domain-names/special-use-domain-names.xhtml) does not provide a publicly reachable mailbox. Do not rely on this address to submit a real support or privacy request. A working contact and the responsible legal identity/address must be supplied before real-user deployment. The project must not identify TIP as the Operator or claim institutional authorization without confirmation.

## 2. Information involved and why it is needed

“Needed” depends on the feature you choose and your authorized role. Registration must not become permission to collect every category below from every user.

| Information | Intended purpose | When needed / choice |
| --- | --- | --- |
| Account email and internal account identifier | Sign-in, account ownership, recovery where supported and linking your own records | Required for an account. Google sign-in uses a verified exact tip.edu.ph address; separate authorized demo logins may use test addresses. |
| First name, last name and middle name, or the explicit No middle name choice | Identify the Student requesting a service and display the correct name | Planned Student profile requirement. Do not invent a middle name or use someone else's identity. |
| Student ID, course/program and year level | Match a service to the Student and relevant academic context | Planned Student profile requirement. Institutional validation and correction procedures must be defined; an entered value alone is not proof of enrollment. |
| Department affiliation | Route departmental access and inquiries to the authorized Secretary | Set through trusted provisioning or approved institutional verification, not self-granted authority. The CCS demo Student is assigned DEP-CCS. |
| Mobile number | A specific service-contact or verification purpose still needs approval | The current planned form includes this field. The Operator must establish its necessity and disclose its use, or make it optional/remove it before real-user collection. Do not imply SMS or phone contact is enabled. |
| Self-reported gender | No necessary app purpose has yet been approved | The current planned form includes this field. It must not be required from real users without a specific, proportionate purpose and applicable lawful basis; otherwise make it optional or remove it. Do not infer gender from a name or picture. |
| Role, office and Staff capability | Limit requests, messages, schedules and payment actions to the appropriate personnel | Required only as trusted authorization attributes. Student registration cannot assign Staff or Tellering access. |
| Optional profile picture | Personalize your own account display | Optional when available. It is not identity proof or consent to public display, facial recognition or advertising. |
| Password authentication material, session and recovery information | Authenticate you, maintain a secure session and handle authorized recovery | Used only for the applicable sign-in method. Normal app screens and office personnel must not receive a readable password or recovery secret. |
| Service request type, destination, reference, notes, requirements and delivery choice | Process, track and deliver the particular service | Needed only for that request. Upload only the information the configured service needs. |
| Evidence and deliverable files, file type/size and access records | Verify requirements, protect document access and establish authorized delivery | Needed only where the selected service requires a file or document release. |
| Fee snapshot, amount/currency, payment method/status/reference and confirmation history | Reconcile an eligible payment, prevent duplicate confirmation and resolve a dispute | Only for a request with a declared required fee. Do not enter card numbers, CVV, bank passwords, PINs or payment OTPs into GoServe messages or attachments. |
| Inquiry messages, office assignment, sender/time and read state | Maintain the thread and route it to authorized participants | Generated when you use office inquiries. Department contact for the CCS Student flow is the CCS Secretary. |
| Appointment time, office/authorized owner, booking status and change history | Reserve capacity and manage cancellation or rescheduling | Generated when you use available consultations. The basic booking flow does not require a clinical history. |
| Notifications, agreement version/time, AI consent choice and withdrawal records | Show relevant updates and demonstrate the choices associated with your account | Required for the respective operation. AI consent remains a separate optional choice. |
| AI question, current conversation context and one selected attachment | Produce the response you explicitly request from Seryo | Optional; only after specific AI notice, consent and an explicit Send action. Provider disclosure and safeguards are release prerequisites. |
| Necessary connection/security metadata, event time, action, result and diagnostic identifiers | Operate the service, prevent unauthorized use and investigate a technical problem | Limit collection to what is actually needed and disclosed. Do not record passwords, raw tokens, private document contents or AI prompts/responses in routine application logs. |

The app's current scope does not need your home address, full birth date, government ID number, contact list, precise background location or advertising identifier for ordinary account registration. A future service must justify and disclose an additional field before collecting it. Avoid adding unrelated health, financial, family or third-party information to free-text boxes.

## 3. Where information comes from

Information comes from what you enter or explicitly upload; Google sign-in identity information needed for authentication; authorized office actions; trusted account and department provisioning; and records generated by the service, such as status changes and acceptance times.

Choosing Google sign-in does not authorize access to your Gmail, Drive, contacts or other unrelated Google content. An identity provider may offer a name or avatar, but this does not authorize automatic storage or public use of additional profile information. The enabled implementation and its requested permissions must match this notice.

## 4. Purpose and lawful processing

Each purpose needs an applicable lawful basis. Depending on the actual arrangement, ordinary personal information may be processed to provide a requested service, meet a legal obligation or on another applicable ground. Where consent is used, it must be specific and informed. Education and health information can be sensitive personal information; an ordinary service need alone does not establish permission to process it. The Operator must establish a valid basis under the rules for sensitive information before collection. These distinctions follow Sections 3, 12 and 13 of the [Philippine Data Privacy Act](https://privacy.gov.ph/Data-Privacy-Act/).

The Operator must approve a purpose-to-lawful-basis record before real deployment. It must not treat this draft, a required checkbox or the word “educational” as legal authority for all processing. A demonstration should use fictional service records and the minimum real account information needed for the authorized test.

Reading this Privacy Notice is not the same as giving consent. Terms acceptance is agreement to app-use rules. A separate consent action is needed when a feature relies on consent. The NPC explains this distinction in [Advisory Opinion No. 2018-058](https://privacy.gov.ph/wp-content/uploads/2022/01/AONo_2018-058.pdf).

## 5. Who may receive information

- **You:** information and documents belonging to your own account, subject to the applicable service checks.
- **The destination office and authorized assignee:** the request, evidence, messages or booking information necessary for the work assigned to them. Ordinary office authority does not permit unrestricted access to every Student's profile.
- **CCS Secretary:** authorized departmental contact for the CCS Student flow. No direct Faculty route is authorized for that flow.
- **Guidance personnel:** information needed for an authorized Guidance booking or inquiry. The booking function must not create broad access to clinical or counseling records.
- **Tellering personnel:** the minimum request identity, reference, amount and payment state needed to handle an eligible cash transaction. A payment task does not justify access to unrelated counseling messages or request files.
- **Authorized technical personnel:** only the access necessary to operate, secure or investigate the service, subject to controls and confidentiality obligations.
- **Supabase and its applicable service providers:** the supporting authentication, database, private file storage and authorized live-update services used by GoServe. The Operator must verify the actual contract, project region and relevant subprocessors before publication.
- **Google:** the authentication interaction you choose through Google sign-in, subject to Google's own applicable notice. Google sign-in does not grant Google permission to read all campus records through GoServe.
- **An enabled payment or AI provider:** only the data needed for the transaction or explicitly requested AI operation, after the provider-specific notice and arrangements are approved. eGovPay and eGovAI are planned integrations; naming them here does not mean transport is active or that their terms have been verified.
- **A legally authorized recipient:** only where a valid legal requirement or other applicable lawful ground justifies the disclosure, limited to what is necessary.

GoServe's proposed policy prohibits selling personal information, using it for targeted advertising, publishing private files or allowing unrelated AI training. A provider must not be treated as meeting that policy until its actual arrangements have been checked. The release notice must name the relevant provider entities, processing locations and any material international transfer arrangements; these are not yet confirmed.

## 6. Seryo and temporary AI data

Seryo must present a separate notice identifying the actual provider, what will be sent, the purpose, retention, training use or prohibition, relevant transfer locations and how to withdraw consent. If those facts are not confirmed, sending personal information to the AI provider must remain disabled.

Allowing AI does not send a question or select a file. Only your explicit Send action starts an eligible operation. The planned attachment limit is one PDF, JPG or PNG up to 10 MB. You may decline AI and continue with ordinary app services, or withdraw AI consent to block new processing.

The current design keeps no persistent Seryo transcript or original-file gallery. Temporary GoServe processing copies are intended for verified deletion after analysis or handled failure. A failed or unverified cleanup must be handled as unresolved; the app must not claim successful deletion or permit unsafe further processing. This requirement does not prove deletion from provider logs, backups or an already submitted operation. Provider-specific limits must be disclosed before activation.

Seryo does not make a final academic, disciplinary, financial or eligibility decision about you. It must not autonomously submit requests, send inquiries, make bookings or execute payments. An ordinary rules-based check, such as rejecting an unauthorized office request, is separate from an AI-generated answer. Ask the responsible office to review an outcome you believe is wrong.

## 7. Storage, permissions and safeguards

The required design uses authenticated connections, account and office access controls, private storage for profile pictures and service files, protected local session credentials and audit records for sensitive operations. Private downloads must remain subject to authorization; a public link is not an acceptable substitute. Office staff must not send private attachments to an unrelated chat or personal account merely for convenience.

Selected-item photo/file access is preferred over broad gallery permission. An enabled office scanner may request camera access and must provide manual code entry. The current scope has no background location, contact harvesting or background microphone function. Permission denial must leave unrelated services available.

Safeguards must be tested before real use. They cannot guarantee that an incident is impossible. The Operator must investigate suspected breaches and assess any required notifications under applicable rules. Security and retention duties are addressed in the [Data Privacy Act's Implementing Rules and Regulations](https://privacy.gov.ph/implementing-rules%20regulations-data-privacy-act-2012/).

## 8. How long records are kept

The following is a **proposed retention schedule for review**, not a statement that automatic deletion is already deployed. The Operator must approve the outstanding periods, backup behavior, legal holds and deletion verification before publication.

| Category | Proposed rule / decision still required |
| --- | --- |
| Account/profile and department information | Keep while the account serves its approved purpose. The post-closure period and process are not yet approved. Remove information no longer necessary, subject to a specific lawful retention ground. |
| Profile picture | Replace or remove through the available controls; remove superseded GoServe copies once they are no longer needed and safe deletion is verified. Storage cleanup and backup timing must be confirmed. |
| Request evidence and released documents | The project proposes deletion of eligible GoServe copies **30 days after the request becomes Completed, Cancelled or Rejected**. This is a proposed project rule, not an official TIP retention policy. A justified legal hold may require a documented exception. |
| Request metadata, inquiries and appointments | Periods and deletion/anonymization criteria remain to be approved separately; the 30-day file rule does not automatically erase these records. |
| Payment records and financial audit | Retain under the applicable approved accounting/legal schedule, still to be determined. Never erase financial evidence merely because a related attachment expires. |
| Terms and consent records | Retain only for the approved period needed to demonstrate the relevant agreement, choice and lawful processing. A specific duration is still required. |
| AI drafts/current chat | No persistent server chat history is planned; confirm actual memory, lifecycle and recovery behavior before release. A draft that remains only on your device has a different lifecycle from a submitted provider request. |
| Temporary AI processing file | Verify deletion after analysis or handled failure; unresolved cleanup blocks unsafe further processing. External provider retention is a separate required disclosure. |
| Security/diagnostic logs and backups | Exact periods, restricted access, rotation, restore handling and disposal must be confirmed. Do not claim that account deletion instantly removes every backup. |

Records should not be kept merely because storage is available. Retention requires a continuing, documented purpose or applicable requirement, consistent with the [NPC's rules on collection, processing and retention](https://privacy.gov.ph/implementing-rules%20regulations-data-privacy-act-2012/).

Deleting the app does not itself delete server records. A deletion request must be assessed against the actual records held, applicable exceptions and provider arrangements. The response must explain any retained category and its reason; it must not make an unsupported promise of immediate universal erasure.

## 9. Your choices and rights

Subject to applicable conditions and exceptions, you may request information about processing, access, correction, objection, erasure or blocking, data portability and appropriate remedies. You may complain to the National Privacy Commission. The NPC describes these protections in its [Data Subject Rights guidance](https://privacy.gov.ph/data-subject-rights/).

Use the published working privacy channel, identifying the relevant account and what you need. GoServeSupport@GoServe.Test is the demonstration contact only and cannot receive a real request over public email. The Operator must use proportionate identity checks, avoid asking for your password and explain its response or any lawful limitation. The Operator must publish the response process and applicable timeframes before launch; this draft does not invent a deadline.

You may choose not to upload a profile picture, not to enable AI, or not to provide unnecessary attachments. Where consent is the basis, withdrawal stops future processing for that purpose, subject to any valid limitation explained to you. It does not automatically erase a completed transaction or records retained on a separate lawful ground. A required service field must have a stated purpose and basis; if it cannot be provided, the Operator must explain the effect and any available alternative.

## 10. Minors, changes and effective date

Before real minor users are admitted, the Operator must confirm an age-appropriate notice, lawful processing arrangements and any required guardian involvement. Do not collect additional identity documents merely to fill an undefined age-verification process. Demonstration participation must not be treated as consent for unrelated research, publication or commercial use.

Material changes to data categories, purposes, recipients or retention must be communicated before the new practice begins. New consent must be obtained where required; silently changing a notice is not permission for a new optional purpose.

**Effective date:** [TO BE SET AFTER APPROVAL]. Until this notice is complete and the enabled implementation matches it, it must remain a review draft rather than an active production privacy notice.
