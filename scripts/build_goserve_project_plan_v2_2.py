from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
from pathlib import Path
from zipfile import ZipFile

from lxml import etree


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "output/docx/IServe_Project_Plan_v2.1.docx"
OUTPUT = ROOT / "output/docx/GoServe_Project_Plan_v2.2.docx"
MARKDOWN = ROOT / "docs/GoServe-project-plan-v2.2.md"
QA = ROOT / "tmp/plan-v2.2"
SOURCE_SHA256 = "bfe75adb08cc500baec52d093a011d11c761ac2d8af9dcb78d12a00dcf60c1db"
DATE = "October 15, 2026"
NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
W = "{" + NS["w"] + "}"


EXACT = {
    "Firebase Authentication only": "Supabase Authentication only; Google OAuth with PKCE and pre-created assigned-account password sign-in",
    "eGovPay sandbox when approved, with teller-confirmed cash as an alternative": "Optional office-declared fees only; free requests skip payment. eGovPay and teller-confirmed cash remain planned payment flows.",
    "Gemini for grounded guidance and temporary document analysis": "Seryo, the eGovAI chatbot. Server credentials are configured; API transport and actions remain disabled pending the API contract and user-supplied procedures.",
    "Firebase is used only for authentication. Do not place application records or files in Firebase products.": "Use Supabase for all authentication, structured records, files, live updates, and trusted server operations. Firebase is excluded from the project.",
    "Keep notifications in the application only. Do not add email notifications, device push, or Firebase Cloud Messaging.": "Keep notifications in the application only. Do not add email notifications or device push notifications.",
    "Students submit requests and private evidence, track status, and receive payment-gated verified PDF deliverables.": "Students submit requests and private evidence, track status, and receive verified PDFs. Apply a payment gate only when the office explicitly declares a fee; free requests skip payment.",
    "Gemini may answer only from approved knowledge records or the current temporary document. Do not retain chat history or temporary analysis files.": "Seryo is the eGovAI chatbot. Enable transport only after its API contract and procedures are supplied. Future answers must use approved knowledge or the current consenting user's temporary document, without retained chat history or analysis files.",
    "Students choose eGovPay or cash. eGovPay uses an approved sandbox or labeled simulator; tellers confirm cash.": "Payment is optional and office-declared. Free requests skip payment; fee-based requests offer eGovPay or cash through Cashiering once trusted payment operations are implemented.",
    "Before checkout, the student chooses payment method egovpay or cash.": "Only an office-declared request fee greater than zero creates a payment step. A free request proceeds without checkout or a payment record; a fee-based request offers egovpay or cash.",
    "The configured request-type fee is authoritative. The Android client cannot supply or modify the amount charged.": "The authorized office declares an optional fee in server-controlled request-type configuration. No office or service is assumed to charge a fee. The Android client cannot supply or change the amount.",
    "A configured, clearly labeled test fee is required before any payment demonstration.": "An authorized office must explicitly declare and label a test fee before demonstrating a paid request. Keep free requests available and bypass payment for them.",
    "Official sandbox is used after access approval; otherwise the app uses a clearly labeled simulator.": "Provider transport remains disabled until checkout, signature, callback, status, and amount contracts are verified. A future simulator must be explicitly labeled and isolated from real payment authority.",
    "The issued eGovPay base URL, access token, settlement template UUID, callback schema, and provider status values remain [OPEN] until sandbox approval. The provider-side already-paid control described for the presentation remains unverified and must not be presented as an official capability until tested. [OPEN]": "The supplied eGovPay base URL, merchant credential, and settlement template are configured on the server. The replacement credential was rotated successfully and is encrypted in Supabase Vault; no credential appears in the APK or this plan. The adapter remains disabled because callback verification, provider status mapping, signatures, and amount canonicalization are unconfirmed. Hosted checkout test controls are user-reported and remain unverified. [CONFIRMED configuration; OPEN provider contract]",
    "Firebase Google sign-in accepts a verified @tip.edu.ph account. The bootstrap function automatically creates a student profile.": "Supabase Google OAuth with PKCE accepts a verified @tip.edu.ph Google identity. The trusted bootstrap function provisions a student profile; Google provider configuration and real sign-in verification remain pending.",
    "Firebase email and password accepts only pre-created demo accounts. The app exposes no public registration path.": "Supabase password sign-in accepts only pre-created assigned/demo accounts with server-provisioned profiles. The app exposes no public password registration path; account provisioning remains pending.",
    "student, staff, and teller routes and records are enforced through server checks and Firebase-token-based RLS.": "student, staff, and teller routes and records are enforced through verified Supabase sessions, trusted bootstrap, server checks, and auth.uid()-based RLS.",
    "The student selects egovpay or cash before checkout. The server loads the configured fee.": "A free request skips payment. For a fee explicitly declared by the office, the student selects egovpay or cash and the server loads the authoritative amount. Payments navigation lists Cashiering only (OFF-CASH).",
    "The Edge Function sends approved knowledge to Gemini and labels the response as guidance, not an official determination.": "A future Edge Function sends approved context to eGovAI for Seryo and labels the response as guidance. The API and Seryo procedures must be supplied and validated before any provider call or action is enabled.",
    "Firebase and Supabase continue to host their existing backend services.": "Supabase hosts authentication and all application backend services.",
    "Google sign-in for verified @tip.edu.ph users and email/password sign-in for pre-created demo accounts only.": "Supabase Google OAuth with PKCE for verified @tip.edu.ph Google users and password sign-in for server-provisioned assigned/demo accounts only.",
    "Uses Supabase's supported Firebase third-party authentication flow to enforce user, office, and capability boundaries.": "Verifies Supabase sessions and uses auth.uid(), server-managed profiles, and RLS to enforce user, office, and capability boundaries.",
    "Provides the approved sandbox transaction and status flow. A labeled simulator follows the same internal contract when credentials are unavailable.": "Targets the supplied eGovPay environment. Credentials are encrypted on the server; transaction/callback/reconciliation transport is disabled until the provider contract is confirmed.",
    "Produces grounded guidance from approved records or the current temporary document through an Edge Function.": "eGovAI provides Seryo, the chatbot. Server configuration exists, but transport and actions stay disabled until API request/response rules and Seryo procedures are supplied.",
    "The Firebase Authentication Android SDK authenticates a Google or pre-created demo account and issues an identity token.": "Supabase Auth authenticates Google through OAuth with PKCE or a pre-created assigned account through password sign-in and returns a Supabase session.",
    "The Android client obtains a current Firebase ID token and presents it to Supabase APIs over HTTPS using the supported third-party authentication integration.": "The Android client refreshes its Supabase session before protected HTTPS calls and presents the access token to the Data API, Storage, and Edge Functions. Refresh tokens are protected with Android Keystore.",
    "A bootstrap Edge Function verifies identity, provisions a verified TIP Google user as student, or loads the pre-created demo profile.": "bootstrap-user verifies the exact token through Supabase Auth getUser, provisions only a verified TIP Google student, or loads the server-provisioned assigned profile. Client metadata never grants a role.",
    "PostgreSQL and Storage RLS evaluate the Firebase identity and application role for every protected operation.": "PostgreSQL and Storage RLS evaluate the Supabase user UUID, application profile, office assignment, and capability for every protected operation.",
    "Verify Firebase identity, enforce account rules, provision verified TIP students, and return application role/capability.": "Verify Supabase identity, enforce Google/password account rules, provision verified TIP students, and return the authoritative profile, role, office, capability, and consent state. Foundation source is deployed.",
    "Apply consent, grounding, file, rate, and provider-failure rules without exposing the API key.": "Planned Seryo/eGovAI gateway: verify the user, approved procedures, grounding, consent, file rules, rate limits, and failure cleanup. Credentials stay in server-only Vault configuration; transport remains disabled.",
    "Firebase identity is accepted and the bootstrap function provisions role student.": "Supabase Google identity is verified and the trusted bootstrap function provisions role student.",
    "Firebase and Supabase identity mapping is incorrect": "Supabase identity and profile mapping is incorrect",
    "Apply the same Firebase-token-based RLS to Realtime reads and test unauthorized subscriptions.": "Apply the same Supabase-session and profile-based RLS to Realtime reads and test unauthorized subscriptions.",
    "What eGovPay sandbox base URL, credential format, settlement template UUID, callback schema, signature details, and provider status values will be issued?": "Base URL, rotated credential, and settlement template are configured. What are the exact checkout payload, HMAC rules, callback authentication/schema, status mapping, reconciliation endpoint, and canonical amount representation?",
    "Which Gemini model and project quota will be available for the non-sensitive demonstration?": "eGovAI base URL and access code are configured server-side. What are the approved request/response contract, agent/task identifiers, Seryo procedures, limits, and permitted temporary-file capabilities?",
    "The only Firebase service used by IServe; it handles Google and pre-created demo email/password identities.": "The sole identity provider in GoServe; it handles verified TIP Google OAuth with PKCE and pre-created assigned/demo password accounts.",
    "Supabase's supported method for accepting Firebase identity tokens for Database, Storage, and Realtime access.": "A Supabase access token binds protected Database, Storage, Realtime, and Edge Function operations to the authenticated user and server-managed profile.",
    "The team must review the demonstration against the Data Privacy Act of 2012 and TIP requirements before any real deployment. This plan is a technical project document, not legal advice. [ASSUMPTION]": "The team must review the demonstration against applicable privacy requirements and TIP requirements before any real deployment. [ASSUMPTION]",
    "Configure at least one free request and one fee-based request so both paths can be shown.": "Configure a free request to demonstrate skipping payment. Add a paid example only after an authorized office declares an optional, clearly labeled test fee; do not assume every office charges fees.",
    "Set a test fee in server-controlled requestTypes data before demonstrating eGovPay or cash.": "For an explicitly fee-based example, set the office-declared test fee in server-controlled requestTypes data before demonstrating eGovPay or cash through Cashiering.",
    "Keep paymentSimulatorEnabled visible in the interface whenever official sandbox credentials are unavailable.": "Do not enable provider transport merely because credentials exist. Keep all future simulator behavior visibly labeled and separate from real payment authority.",
}

PREFIX = {
    "IServe is a native Android application for TIP Manila": "GoServe is a native Java/XML ConstraintLayout Android application for TIP Manila's Casal Campus and Arlegui Campus. The agreed complete demonstration includes office information, document requests and private evidence, optional office-declared fees, eGovPay or cash for paid requests, tracking, verified PDFs, inquiries, and guidance/faculty consultations. Student navigation is Home, Requests, Consult, Inbox, AI Chat, and More. AI Chat opens Seryo directly. Current delivery status is stated in Section 1.5. [CONFIRMED]",
    "Technical references: Supabase Firebase": "Technical references: Supabase Google OAuth, https://supabase.com/docs/guides/auth/social-login/auth-google; Supabase Auth and RLS, https://supabase.com/docs/guides/database/postgres/row-level-security; eGovPay API catalog, https://platforms.e.gov.ph/api-catalogs/egovpay. Provider procedures and payloads remain open until supplied and verified.",
    "Android references: Firebase": "Android references: Android network operations, https://developer.android.com/develop/connectivity/network-ops/connecting; CameraX architecture, https://developer.android.com/media/camera/camerax/architecture. Google sign-in uses Supabase OAuth rather than a Firebase SDK.",
    "The issued eGovPay base URL": EXACT["The issued eGovPay base URL, access token, settlement template UUID, callback schema, and provider status values remain [OPEN] until sandbox approval. The provider-side already-paid control described for the presentation remains unverified and must not be presented as an official capability until tested. [OPEN]"],
}

GENERAL = (
    ("IServe", "GoServe"), ("iserve.demo", "goserve.demo"),
    ("Version 2.1", "Version 2.2"), ("v2.1", "v2.2"),
    ("October 13, 2026", DATE),
    ("Firebase Authentication", "Supabase Authentication"),
    ("Firebase third-party authentication", "Supabase session authentication"),
    ("Firebase-token-based RLS", "Supabase-session-based RLS"),
    ("Firebase identity", "Supabase identity"),
    ("Firebase tokens", "Supabase JWTs"),
    ("Firebase ID tokens", "Supabase access tokens"),
    ("firebaseUid", "authUserId"),
    ("Firebase project", "Supabase Auth configuration"),
    ("Firebase sign-in", "Supabase sign-in"),
    ("Gemini assistant", "Seryo chatbot"),
    ("Gemini assistance", "Seryo eGovAI assistance"),
    ("Gemini knowledge answer", "Seryo grounded answer"),
    ("Gemini", "eGovAI"),
    ("ask-gemini", "ask-seryo (planned)"),
)


STATUS_ROWS = (
    ("WP-1 Foundation", "Implemented; final verification recorded below", "Java/XML hosts, light/dark themes, domain states, responsive layouts, Supabase auth/session plumbing, role routing, consent, directory, settings, and bundled brand assets. Physical-device verification remains pending."),
    ("WP-2 Identity and RLS", "Foundation deployed; setup and integration pending", "Supabase schema, bootstrap-user, consent RPC, and foundation RLS exist. Configure Google OAuth and create assigned accounts; verify real Android sign-in and all private record/file policies."),
    ("WP-3 Offices", "Directory implemented; publishing planned", "Five labeled demo offices, search, selected filters, office detail, and Cashiering-only Payments routing. Live staff status/announcement writes and Realtime remain planned."),
    ("WP-4 Requests and files", "UI preview; live flow planned", "My Requests preview and status design do not submit or mutate requests. Implement private evidence, server status history, verified PDF delivery, free/paid branching, and retention."),
    ("WP-5 Payments", "Server configuration prepared; disabled", "eGovPay credential rotated and encrypted in Vault. Provider contract, authoritative checkout, authenticated callbacks, reconciliation, teller cash confirmation, audit, and simulator are still pending."),
    ("WP-6 Consultations", "Planned", "Implement schedules, atomic capacity, booking, cancellation, and rescheduling for guidance and CCS faculty/secretary."),
    ("WP-7 Inquiries and notifications", "Planned", "Implement authorized threads, assignments, messages, read state, and in-app Realtime notifications."),
    ("WP-8 Pickup", "Planned", "Implement hashed single-use token issuance/consumption, CameraX QR scan, manual entry, reuse denial, and completion audit."),
    ("WP-9 Seryo", "UI and server configuration prepared; disabled", "Direct AI Chat destination and consent/attachment panels exist. eGovAI access code/base URL are encrypted server-side with enabled=false. Procedures are blank; chat transport, uploads, and actions remain disabled."),
    ("WP-10 Integrated verification", "Pending", "Complete AT-01 through AT-38, role/RLS negative tests, provider contract tests, physical-device APK installation, browser-return/keyboard/inset checks, and the end-to-end demonstration."),
)


def paragraph_text(node) -> str:
    return "".join(node.itertext()) if node.tag == W + "t" else "".join(node.xpath(".//w:t/text()", namespaces=NS))


def replace_paragraph(node, text: str) -> None:
    texts = node.xpath(".//w:t", namespaces=NS)
    if not texts:
        run = etree.SubElement(node, W + "r")
        texts = [etree.SubElement(run, W + "t")]
    texts[0].text = text
    texts[0].set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    for item in texts[1:]:
        item.text = ""


def rewrite(text: str) -> str:
    suffix = " [CONFIRMED]" if text.endswith(" [CONFIRMED]") else ""
    unlabelled = text[:-len(suffix)] if suffix else text
    changed = EXACT.get(text, EXACT.get(unlabelled, text) + (suffix if unlabelled in EXACT else ""))
    for prefix, replacement in PREFIX.items():
        if changed.startswith(prefix):
            changed = replacement
            break
    for old, new in GENERAL:
        changed = changed.replace(old, new)
    return changed


def clone_paragraph(template, text: str):
    node = copy.deepcopy(template)
    replace_paragraph(node, text)
    return node


def make_table(template, headers, rows, widths):
    node = copy.deepcopy(template)
    source_rows = node.findall(W + "tr")
    header, body = source_rows[0], source_rows[1]
    for row in source_rows:
        node.remove(row)
    grid = node.find(W + "tblGrid")
    for item in list(grid):
        grid.remove(item)
    for width in widths:
        etree.SubElement(grid, W + "gridCol", {W + "w": str(round(width * 1440))})
    for index, values in enumerate((headers, *rows)):
        row = copy.deepcopy(header if index == 0 else body)
        for cell, value, width in zip(row.findall(W + "tc"), values, widths):
            for extra in cell.findall(W + "p")[1:]:
                cell.remove(extra)
            replace_paragraph(cell.find(W + "p"), value)
            cell.find(W + "tcPr").find(W + "tcW").set(W + "w", str(round(width * 1440)))
        node.append(row)
    return node


def special_updates(document) -> None:
    paragraphs = document.xpath(".//w:p", namespaces=NS)
    for p in paragraphs:
        text = paragraph_text(p)
        if text == "Native phone layouts, widgets, navigation, and activity lifecycles for students, staff, faculty, guidance, and tellers. The client never holds provider or eGovAI secrets.":
            replace_paragraph(p, "Java 17 and XML ConstraintLayout with AndroidX fragments, ViewModel/LiveData, and view binding; minimum API 26, compile/target API 35. Native role navigation and lifecycle handling. Provider and service secrets never enter the APK.")
        elif "Runs requests off the UI thread; sends Supabase" in text:
            replace_paragraph(p, "Runs HTTPS requests off the UI thread, refreshes Supabase sessions, and sends current access tokens to protected APIs. Realtime subscriptions remain planned and must follow the same RLS boundaries.")
        elif "Builds an installable APK" in text:
            replace_paragraph(p, "Android Studio and Gradle build the Java/XML application into an installable APK. Supabase is the sole authentication and application backend; no Firebase dependency is required.")
        elif text == "users":
            replace_paragraph(p, "profiles (foundation); users (planned extension)")
        elif text == "id, authUserId, email, displayName, role, staffCapability, officeId, isDemo, consentAcceptedAt":
            replace_paragraph(p, "Foundation: id (Supabase Auth UUID), email, display_name, role, staff_capability, office_id, is_demo, consent_accepted_at. Future record extensions retain the same ownership boundaries.")
        elif text == "demoMode, allowedEmailDomain, paymentSimulatorEnabled, providerConfigurationState":
            replace_paragraph(p, "demoMode, allowedEmailDomain, paymentSimulatorEnabled, providerConfigurationState; server-only eGovPay/eGovAI settings include enabled=false until contracts are verified. Seryo procedures are not populated.")
        elif text == "eGovPay approval or credentials arrive late":
            replace_paragraph(p, "eGovPay provider contract remains unconfirmed")
        elif text == "Official sandbox flow may be unavailable.":
            replace_paragraph(p, "Configured credentials alone do not make payment transport safe or operational.")
        elif text == "Keep the provider adapter isolated and use a clearly labeled simulator with the same internal states.":
            replace_paragraph(p, "Keep the adapter disabled until payload, signature, callback, status, and amount contracts pass tests. A future simulator must be clearly labeled and cannot confirm real payments.")
        elif text == "What submission artifacts does the subject require in addition to the APK and this plan?":
            replace_paragraph(p, "What submission artifacts does the subject require in addition to the APK, editable plan, and PDF plan; and which physical Android devices will be used for final verification?")
        elif text == "These addresses are suggested non-production demo identifiers. Passwords, provider secrets, and real student information do not appear in this plan. [ASSUMPTION]":
            replace_paragraph(p, "These are proposed non-production identifiers, not created or verified accounts. Trusted administration must provision Supabase Auth users and matching profiles before real sign-in testing. Passwords, access codes, merchant tokens, and real student information do not appear in this plan. [ASSUMPTION]" )
        elif text == "Keep paymentSimulatorEnabled visible in the interface whenever official sandbox credentials are unavailable.":
            replace_paragraph(p, "Do not enable provider transport merely because credentials exist. Keep all future simulator behavior visibly labeled and separate from real payment authority.")
        elif text == "Configure at least one free request and one fee-based request so both paths can be shown.":
            replace_paragraph(p, "Configure a free request to demonstrate skipping payment. Add a paid example only after an authorized office declares an optional, clearly labeled test fee; do not assume every office charges fees.")
        elif text == "Set a test fee in server-controlled requestTypes data before demonstrating eGovPay or cash.":
            replace_paragraph(p, "For an explicitly fee-based example, set the office-declared test fee in server-controlled requestTypes data before demonstrating eGovPay or cash through Cashiering.")
        elif text == "Student selects eGovPay for a fee-based request.":
            replace_paragraph(p, "Student selects eGovPay only when the owning office declares a fee greater than zero.")
        elif text == "Student selects cash and receives a request reference with the configured amount due.":
            replace_paragraph(p, "For an office-declared fee, the student selects cash and receives a reference and server-loaded amount due; a free request skips this flow.")
        elif text == "Payment is confirmed or no fee applies, and the office is working on the request.":
            replace_paragraph(p, "The office is working on the request after authoritative confirmation of an applicable fee, or after bypassing payment for a free request.")
        elif text == "Configured request-type fee is authoritative.":
            replace_paragraph(p, "Only an optional fee declared by the authorized office applies; the server-loaded amount is authoritative.")
        elif text == "id, officeId, name, fee, deliveryMode, requirementsText, active, isDemo":
            replace_paragraph(p, "id, officeId, name, optional office-declared fee (null/zero means free), deliveryMode, requirementsText, active, isDemo; trusted server configuration records fee authority.")
        elif text == "A eGovAI response limited to approved knowledgeArticles or the current temporary document and labeled as guidance.":
            replace_paragraph(p, "A Seryo response from eGovAI, limited to approved knowledgeArticles or the current consenting user's temporary document and labeled as guidance.")


def append_progress(document, verification: str) -> None:
    body = document.find(W + "body")
    children = list(body)
    section_2 = next(x for x in children if x.tag == W + "p" and paragraph_text(x).startswith("2. Confirmed P1"))
    subheading = next(x for x in children if x.tag == W + "p" and paragraph_text(x) == "1.4 Out of scope")
    normal = next(x for x in children if x.tag == W + "p" and paragraph_text(x).startswith("The project demonstrates"))
    role_table = next(x for x in children if x.tag == W + "tbl" and paragraph_text(x).startswith("RoleWhoAccess boundary"))
    nodes = [
        clone_paragraph(subheading, "1.5 Current implementation status"),
        clone_paragraph(normal, "As of October 15, 2026, the Android and Supabase foundation is implemented. Preview screens demonstrate visual structure and navigation only. A preview never authenticates a user, submits a request, books a consultation, confirms a payment, or fabricates an eGovAI answer. The complete P1 application remains under implementation. [CONFIRMED]"),
        make_table(role_table, ("Package", "Current state", "Remaining work and evidence boundary"), STATUS_ROWS, (1.15, 1.55, 4.3)),
        clone_paragraph(subheading, "1.6 Verification and next delivery"),
        clone_paragraph(normal, verification),
        clone_paragraph(normal, "Foundation checks and UI previews do not complete AT-01 through AT-38. The next delivery must establish real assigned-account/Google sign-in and finish live office, request/file, payment, consultation, inquiry, pickup, and Seryo contracts in dependency order. No physical Android device or emulator has yet verified the complete integrated workflow. [OPEN]"),
    ]
    for node in nodes:
        body.insert(body.index(section_2), node)


def append_design(document) -> None:
    body = document.find(W + "body")
    children = list(body)
    section_11 = next(x for x in children if x.tag == W + "p" and paragraph_text(x).startswith("11. Demonstration"))
    subheading = next(x for x in children if x.tag == W + "p" and paragraph_text(x) == "1.4 Out of scope")
    normal = next(x for x in children if x.tag == W + "p" and paragraph_text(x).startswith("The project demonstrates"))
    for text in (
        "10.1 Navigation and current UI delivery",
        "Student tabs are Home, Requests, Consult, Inbox, AI Chat, and More. AI Chat opens Seryo directly; it is not hidden in More. Staff and teller routes retain role-scoped destinations. Directory Payments includes only Cashiering (OFF-CASH), regardless of another office's stored category. Office detail may route a user to Cashiering when a declared fee applies; routing does not create or confirm a payment. [CONFIRMED]",
        "My Requests and Seryo chat, consent, and attachment panels are UI previews with honest disabled states. Seryo is a chatbot powered by eGovAI. Its supplied access code and base URL are encrypted server-side; enabled=false and blank procedures keep provider transport, uploads, and actions disabled until their contracts are issued. [CONFIRMED]",
        "10.2 Typography brand and responsive layout",
        "The user-approved Arvo/Lato pairing replaces the original Figma Archivo/Roboto typography. Bundle actual Arvo Bold 700 for primary headlines and headings, Lato Regular 400 for body/UI copy, and Lato Semi-Bold 600 for labels, captions, buttons, and navigation. Android sizes/line heights are headline 30/38sp, section and toolbar 20/28sp, office/card heading 16/24sp, body 16/24sp, supporting copy 14/20sp, label 14/20sp, and caption/navigation 12/16sp. [CONFIRMED]",
        "Use the supplied GoServe square logo at 56dp on sign-in, 112dp in authentic loading states, and 40dp in the Home masthead; preserve aspect ratio and theme contrast. Loading reflects real authentication work and never uses fabricated percentages or delays. Seryo uses the supplied chatbot mark where appropriate. [CONFIRMED]",
        "The monochrome light/dark Figma palette remains the design basis. Use sentence case, natural wrapping, real system/keyboard insets, minimum 48dp touch areas, 24dp auth margins, 20dp main margins, and a centered maximum content width of 600dp. Reflow at 320/360/390/412/480dp and font scales 1.0/1.3/2.0; use one office column at compact widths or large text. Text and final list content must remain reachable above bottom navigation. [CONFIRMED]",
    ):
        node = clone_paragraph(subheading if text.startswith("10.") else normal, text)
        body.insert(body.index(section_11), node)


def markdown(document) -> str:
    lines = []
    for node in document.find(W + "body"):
        if node.tag == W + "p":
            text = paragraph_text(node).strip()
            if not text:
                continue
            style = node.find("w:pPr/w:pStyle", namespaces=NS)
            style_id = style.get(W + "val") if style is not None else ""
            if text == "GoServe":
                lines.append("# GoServe project plan and grounding specification")
            elif style_id == "Heading1":
                lines.append("## " + text)
            elif re.match(r"^\d+\.\d+ ", text):
                lines.append("### " + text)
            elif style_id == "ListBullet":
                lines.append("- " + text)
            else:
                lines.append(text)
            lines.append("")
        elif node.tag == W + "tbl":
            rows = node.findall(W + "tr")
            values = [[paragraph_text(cell).replace("|", "\\|").replace("\n", "<br>") for cell in row.findall(W + "tc")] for row in rows]
            if len(values[0]) == 1:
                lines.extend((values[0][0], ""))
            else:
                lines.append("| " + " | ".join(values[0]) + " |")
                lines.append("| " + " | ".join("---" for _ in values[0]) + " |")
                for row in values[1:]:
                    lines.append("| " + " | ".join(row) + " |")
                lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def build(verification: str) -> None:
    source_hash = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    if source_hash != SOURCE_SHA256:
        raise ValueError("The retained v2.1 source changed; inspect it before authoring.")
    with ZipFile(SOURCE) as archive:
        parts = {name: archive.read(name) for name in archive.namelist()}
    original_parts = dict(parts)
    parser = etree.XMLParser(remove_blank_text=False)
    changed = []
    for name, raw in list(parts.items()):
        if name == "word/document.xml" or re.match(r"word/(header|footer)\d+\.xml$", name):
            tree = etree.fromstring(raw, parser)
            for paragraph in tree.xpath(".//w:p", namespaces=NS):
                old = paragraph_text(paragraph)
                new = rewrite(old)
                if old != new:
                    replace_paragraph(paragraph, new)
            if name == "word/document.xml":
                special_updates(tree)
                append_progress(tree, verification)
                append_design(tree)
                document = tree
            data = etree.tostring(tree, encoding="UTF-8", xml_declaration=True, standalone=True)
            if data != raw:
                parts[name] = data
                changed.append(name)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    MARKDOWN.parent.mkdir(parents=True, exist_ok=True)
    QA.mkdir(parents=True, exist_ok=True)
    with ZipFile(OUTPUT, "w") as archive:
        with ZipFile(SOURCE) as original:
            for info in original.infolist():
                archive.writestr(info, parts[info.filename])
    md = markdown(document)
    MARKDOWN.write_text(md, encoding="utf-8")
    section_ids = re.findall(r"^## (\d+)\.", md, re.M)
    test_ids = re.findall(r"\| (AT-\d{2}) \|", md)
    assert section_ids == [str(x) for x in range(17)], section_ids
    assert test_ids == [f"AT-{x:02d}" for x in range(1, 39)], test_ids
    assert "Gemini" not in md and "IServe" not in md
    unchanged = [name for name in parts if parts[name] == original_parts[name]]
    assert set(changed) <= {"word/document.xml", "word/footer1.xml"}, changed
    report = {"date": DATE, "reference_sha256": source_hash, "source_unchanged": hashlib.sha256(SOURCE.read_bytes()).hexdigest() == source_hash,
              "sections": len(section_ids), "acceptance_tests": len(test_ids), "changed_package_parts": changed,
              "preserved_package_parts": unchanged, "outputs": [str(OUTPUT), str(MARKDOWN)]}
    (QA / "structure-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "preserved_package_parts"}, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verification", default="The current source contains focused domain, authentication lifecycle, layout responsiveness, directory interaction, office/payment routing, navigation, typography, and native-render regression tests. The latest complete Android build, test, and lint gate is being rerun before its results are recorded here. Backend foundation checks and live transactional RLS checks have passed previously; they do not verify the planned integrated workflows. [OPEN latest gate]")
    args = parser.parse_args()
    build(args.verification)


if __name__ == "__main__":
    main()
