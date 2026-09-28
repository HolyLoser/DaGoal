# Authentication & Sign-Up Robustness & UX Improvement Plan

## Problem Statement & Common Sign-In/Sign-Up Issues

Modern Android applications require a frictionless yet robust authentication experience. Based on common user pain points and industry standards (seen in apps like Duolingo, Strava, and fitness/habit gamification apps), standard login/signup flows often encounter:
1. **Double-Tapping / Multiple Submissions**: Users tapping "Sign In" or "Create Account" multiple times while waiting for network response, leading to duplicate requests or race conditions.
2. **Ambiguous Error Feedback**: Cryptic Firebase or network errors confusing users (e.g., "auth/network-request-failed" or raw exception strings).
3. **Network Drops / Offline States**: Users attempting login/signup without internet connection when cloud sync is expected, or lacking a clear offline experience path.
4. **Keyboard / UI Obstruction**: Buttons or error messages hidden behind the software keyboard (IME).
5. **Input Validation Gaps**: Weak passwords, leading whitespace in emails, or missing format checks.

## User Review Required

> [!IMPORTANT]
> This plan focuses on hardening the existing `AuthEmailActivity`, `AuthPasswordActivity`, `SignUpActivity`, and `AuthOptionsActivity` without altering core offline fallbacks or local database logic.

## Proposed Enhancements & Contingency Plans

### 1. Request State Management (Preventing Double-Tapping & Loading State)
- **Problem**: Users clicking primary buttons multiple times during Firebase auth tasks.
- **Solution**:
  - Disable buttons (`btnPasswordLogin`, `btnCreateAccount`, `btnEmailNext`) immediately upon click.
  - Show loading feedback (change button text to "Signing in..." / "Creating account..." or show a progress indicator) and re-enable buttons if authentication fails.

### 2. Enhanced Error Handling & User-Friendly Messages
- **Problem**: Raw Firebase exceptions (`FirebaseAuthInvalidCredentialsException`, `FirebaseAuthUserCollisionException`, etc.) can be technical.
- **Solution**:
  - Map Firebase exceptions to clear, actionable guidance:
    - Invalid email / wrong password → "Incorrect email or password. Please try again."
    - User collision (Sign Up) → "An account with this email already exists. Please log in instead."
    - Network failure → "Network connection error. You can continue in offline mode or check your connection."

### 3. IME Keyboard & Input Polish
- **Problem**: Users hitting 'Enter' on the software keyboard doesn't submit the form.
- **Solution**:
  - Add `setOnEditorActionListener` on password and email EditTexts to trigger `processLoginAuthentication()` or `processAccountRegistration()` directly on `EditorInfo.IME_ACTION_DONE`.

### 4. Input Sanitization
- **Problem**: Accidental leading/trailing whitespace in email fields.
- **Solution**:
  - Automatically `.trim()` inputs across all auth activities.

## Files Involved

- [MODIFY] [AuthEmailActivity.java](file:///C:/Users/Shaun/AndroidStudioProjects/DaGoal/app/src/main/java/com/stipasay/dagoal/AuthEmailActivity.java)
- [MODIFY] [AuthPasswordActivity.java](file:///C:/Users/Shaun/AndroidStudioProjects/DaGoal/app/src/main/java/com/stipasay/dagoal/AuthPasswordActivity.java)
- [MODIFY] [SignUpActivity.java](file:///C:/Users/Shaun/AndroidStudioProjects/DaGoal/app/src/main/java/com/stipasay/dagoal/SignUpActivity.java)

## Verification Plan

### Automated Tests
- Build project using `gradle_build("app:assembleDebug")`.

### Manual Verification
- Test empty email / invalid email validation.
- Test short password validation in sign-up.
- Test button state disabling during login/signup actions.
- Test IME action "Done" on keyboard submission.
