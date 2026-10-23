# Showcase accounts

On21October the owner approved exactly six demo accounts, excluded Faculty, specified that the CCS Student's department contact is the Secretary, and requested one shared demo password. All six Auth logins and trusted profiles are now created and verified in the live GoServe project. Passwords are kept only in the approved private file `C:/Users/Rieecheeze/Documents/GoServe Demo Credentials.txt`, outside app source. These are application login addresses, not hosted mailboxes. All profiles have `is_demo=true`; none is attached to the owner's real Google identity.

| Demo login | Role | Office / capability |
| --- | --- | --- |
| `registrar@demo.goserve.test` | Staff | `OFF-REG`, standard office Staff |
| `accounting@demo.goserve.test` | Staff | `OFF-ACC`, standard office Staff |
| `teller@demo.goserve.test` | Teller | `OFF-CASH` |
| `guidance@demo.goserve.test` | Staff | `OFF-GCC`, `guidance` |
| `secretary.ccs@demo.goserve.test` | Staff | `DEP-CCS`, `secretary` |
| `student.ccs@demo.goserve.test` | Student | College of Computer Studies (`DEP-CCS`); no Staff office or capability |

Office IDs/names were verified from the live catalog. Guidance and CCS Secretary are Staff capabilities. No Faculty account was created. The Secretary is the only provisioned Staff profile in the Student's department. CCS is the Student's department; no degree program or real institutional student identifier is assumed. The owner’s Secretary-only contact requirement supersedes the older Faculty consultation requirement; reconcile those legacy source routes before the broader consultation rollout.

Auth users were created through the signed-in Supabase dashboard with automatic confirmation and no invitations. Trusted public profiles were inserted by selecting the corresponding Auth user IDs by exact email; no managed Auth rows were written directly. The owner then replaced the unique-password request with a shared demo password. The initial Registrar password was changed through the official signed-in Auth API, and fresh password sign-in succeeded for all six. Each token could read only its own expected profile, and the verification sessions were closed. Existing real profiles were preserved. No institutional Terms were accepted and no legal text was seeded. Reserved demo addresses cannot receive forgot-password mail; real recovery tests still need a controlled mailbox.

CCS association uses the additive nullable `profiles.department_id` field, constrained to an existing `DEP-` catalog entry. The demo Student has `department_id='DEP-CCS'`, `role='student'`, `office_id=null` and `staff_capability=null`. The Secretary has office/department `DEP-CCS` and capability `secretary`. No other Students were backfilled and no authority came from user metadata. Student self-service profile updates cannot change this trusted affiliation. CLI-generated source migration `20261020162841_profile_department.sql` was applied independently as remote migration `20261020222356`, name `profile_department`, to support this approved provisioning.

Live proof: `output/validation/demo-accounts/provisioning.json`, `supabase-six-users.png` and `docs/GoServe-demo-accounts-live-2026-10-21.md`. The existing app URL/key performed real Auth password sign-in and authenticated REST own-profile reads for all six. Full native onboarding remains unverified because the first nine core migrations, revised account Edge Functions and approved Terms are still pending. The old live bootstrap response still fails the revised native contract. Creating these logins does not claim that broader app workflows are deployed.
