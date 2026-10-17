# Seryo eGovAI integration

Seryo is GoServe's AI chatbot. Android opens it from the AI Chat tab.

The supplied access code belongs in Supabase Vault. Server configuration is stored in
`goserve_private.egovai_configuration`; only `service_role` may read its RPC. Neither the
access code nor payment tokens belong in Android, source files, documentation, or logs.

The integration is disabled. Agent procedures are blank until the user supplies Seryo's
tasks and operating instructions. The supplied base URL alone does not establish an API
route, authentication header, request/response schema, streaming protocol, model selection,
tool authorization, quota, or retention contract. No guessed provider calls are implemented.

The Android panels currently show setup pending, privacy information, and the planned
temporary attachment flow. Send and file selection are disabled. Drafts are not persisted,
no questions are transmitted, and no AI responses or successful file cleanup are simulated.

Before enabling chat, implement the documented eGovAI transport behind an authenticated
Supabase Edge Function. Define grounding and allowed operations from the user's procedures;
keep request, payment, and private-file permissions enforced by trusted server operations.
AI output cannot declare fees, confirm payments, release files, or change a request status
without an explicitly authorized server operation. Obtain separate consent before temporary
analysis, enforce one PDF/JPG/PNG up to 10 MB, and verify cleanup on success and failure.
