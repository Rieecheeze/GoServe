# Profile pictures: local implementation and rollout

21 October 2026. Native implementation passes the377-test gate. The owner-approved database contract is now live in remote20261021005108/goserve_v23_core_accounts_workflows_photos. Installed-device and authenticated photo transfer acceptance remain pending; deployment alone does not prove that flow.

## Native flow

More → Profile picture loads the current account's saved image. Choose photo opens the Android single-image picker, with its document-picker fallback. Cancelling preserves the current preview. Selection prepares an unsent, memory-only draft; Save is explicit. Remove stages a change that also needs Save. Reload saved photo discards the local draft and reads the canonical state.

The worker bounds source input to12MiB, accepts decoded JPEG/PNG/WebP, rejects nonpositive dimensions, an axis over20,000px or a total over80million pixels, downsamples, applies EXIF orientation and emits a JPEG no larger than512px per edge and1MiB. Re-encoding removes original metadata. No broad photo/storage permission or persistent URI grant was added. The stored image keeps its aspect ratio; the circular preview center-crops its display without stretching a wide image.

The retained ViewModel serializes loading, preparation and saving, keeps drafts/operation identities only in memory, rejects double submissions, and checks the pinned account before work and publication. An uncertain save retains the same operation identity and bytes for retry. A stale version requires an explicit reload. Account replacement clears the picture/draft and prevents publication. Saving locks Back; loading/selection leave Back available. All buttons use the shared press motion, and loading uses the existing reduced-motion-aware brand loader.

## Local migration and data contract

CLI-generated source: `backend/migrations/20261020233835_profile_avatars.sql`.

- Adds nullable `profiles.avatar_path` and nonnegative `avatar_version`, preserving role, office, department and the unrelated account version.
- Creates private `profile-avatars`, JPEG-only with a1MiB upload limit. Restrictive bucket policies prevent a permissive authenticated policy from publishing or deleting this bucket.
- A private registry holds server-issued owner paths in pending/current/retired states. Only a prepared pending path can be uploaded or bound. A retired path never becomes current again.
- Restrictive Storage policies enforce owner access despite another permissive policy. Overwrite and current-object deletion are denied. Retired objects may be deleted through the owner's Storage API, preventing a delayed cleanup from removing a newer photo.
- `profile_avatar()` returns only own path, version and up to20 retired paths that still have Storage metadata.
- `prepare_profile_avatar(expected_version,key)` issues a private immutable path; `set_profile_avatar(path,expected_version,key)` atomically binds it or removes the pointer. Both use operation keys and the versioned profile lock.
- `discard_profile_avatar(path)` retires an abandoned pending path; it rejects current paths. It is available for authorized cancellation/maintenance. The current native draft flow does not call it automatically after an uncertain save.

Save verifies Storage-declared MIME and size. This is not independent server-side byte parsing. The native decoder normalizes selected files; private downloads are bounded and checked before display. No server image parser is claimed.

Native uploads use authenticated POST without upsert, private reads use authenticated GET, and retired cleanup uses the Storage DELETE API. Tokens come from the pinned account lease and are never written to logs or an image URL. Duplicate-upload responses can recover an uncertain first upload through the same versioned save. Successful profile save is separate from cleanup success.

## Cleanup limits

Cleanup of retired files is attempted when the account loads or saves its picture and retried on a later load if it fails. The UI acknowledges a profile update, not universal physical deletion. The backend registry prevents retired paths from being rebound.

An interrupted prepare/upload can leave a pending object. A scheduled server cleanup for abandoned pending objects, and its approved retention interval, remain a deployment decision. Do not report scheduled cleanup, verified universal erasure or backup deletion as implemented. No raw `storage.objects` deletion is performed by production app code.

## Deployment order and acceptance still needed

The live department migration is recorded as remote20261020222356/profile_department. The missing first nine and this photo migration were subsequently deployed together as remote20261021005108/goserve_v23_core_accounts_workflows_photos. Do not reapply either the tenth or the ten bundled source files on this managed installation. Fresh local databases still apply all eleven source migrations in order. The source manifest and original-field preservation proof are in output/validation/account-rollout/.

No active Terms document or user acceptance was inserted. The owner named GoServe and supplied GoServeSupport@GoServe.Test for the legal draft's demonstration contacts. Those edits are not approval to activate the draft. Account bootstrap v2 is deployed and six demo API-readiness checks pass; approved Terms and trusted profile completion remain prerequisites for installed live-role acceptance.

Before claiming real-device completion, test an authorized Student and office account: picker cancellation, replacement/removal, restart and rotation, private cross-account denial, poor connectivity during each upload/commit phase and retired-file cleanup. The local native render fixtures are synthetic Android graphics, not screenshots of a live authenticated account. The extra API26 unit-runtime attempt was unavailable; actual API26/device photo acceptance remains pending.

## Local evidence

Fifteen SQL cases cover private defaults, roles/ownership, profile readiness, object metadata, operation-key replay, stale versions, direct writes, overwrite/current-delete denial, retirement, removal and bucket-policy isolation. Full backend303 checks pass in `tmp/backend-profile-avatars-final.log`.

Native image/repository, ViewModel and interaction tests include picker launch without submission, cancellation, invalid selection, JPEG/EXIF normalization, replay/uncertain transport, queued account replacement, view destruction, explicit removal/save and reachable feedback at320dp/2x text. Render fixtures are under `output/validation/profile-photo/`. The full native gate and current APK identity must be read from the latest validation artifact; focused tests alone do not prove an installed live backend flow.
