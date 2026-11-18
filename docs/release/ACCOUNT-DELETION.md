# Firebase account deletion rollout

## Manual requests on the Spark plan

Keep Firebase on Spark and leave `dagoalFirebaseAccountCleanup` unset. Settings → Account → Delete Account offers an email draft for registered users. The public account deletion page provides the same address when hosted.

1. Verify that the requester controls the account email. Never ask for a password. Find the account in Firebase Authentication and record its UID for the request.
2. Delete the verified Auth account. Then delete the matching `users/{UID}` and `chest_claim_limits/{UID}` documents if present. Keep the request open until all checks are complete so cleanup can resume if interrupted.
3. Confirm completion. Explain how to clear DaGoal storage on any device where they want local data removed too.

## Optional automated rollout

The steps below require Blaze. Skip them for the Spark deployment.

DaGoal deletes the signed-in Firebase Auth account after reauthentication. The Delete User Data extension handles Firestore cleanup after the Auth deletion event. DaGoal records the pending UID locally so it can finish local cleanup after a restart.

1. In a test Firebase project, install the [Delete User Data extension](https://firebase.google.com/docs/extensions/official/delete-user-data). Use the region near Firestore. Set Cloud Firestore paths to `users/{UID},chest_claim_limits/{UID}`. Leave auto discovery off. Leave Realtime Database and Storage paths blank for DaGoal's current data model.
2. Verify the extension with a disposable account. Add a profile document and a chest limit document keyed by its UID. Delete the disposable Auth account and check extension logs and Firestore for both deletions. Also verify that a second test account's documents remain.
3. Connect a test build to the test project and enable the flow with `-PdagoalFirebaseAccountCleanup=true`. Use disposable accounts to test incorrect credentials, a disconnected device, and closing DaGoal before and after Firebase accepts deletion. Confirm that a rejected request preserves progress and that an accepted request removes the Auth user, clears local data, and triggers cleanup of both configured Firestore paths.
4. Repeat the extension setup and disposable-account verification on the release Firebase project before using the flag for distribution. To include the flag in the signing helper without putting it in source control, set `ORG_GRADLE_PROJECT_dagoalFirebaseAccountCleanup=true` in the build environment.

The flag defaults to false. Registered users then see the support contact option, and guest data deletion remains available. The app confirms local cleanup only. Use extension logs to confirm remote cleanup.
