# General AI Agent Development Rules

## Purpose

This file contains general safety and development rules for any AI coding agent working on this project.

These rules apply regardless of which AI agent, IDE, coding assistant, or model is being used.

The project contains existing code and functionality. Treat the existing project as a working system that must be protected.

The core principle is:

> Understand first. Change only what is necessary. Preserve existing behavior. Verify everything after changing it.

---

# 1. Existing code is valuable

Do not treat the project as a blank project.

Before changing code, understand:

- What the code does
- What depends on it
- What it depends on
- How data flows through it
- How users interact with it
- Which other features rely on it

Do not rewrite working code simply because another implementation looks cleaner.

---

# 2. Inspect before editing

Before making a non-trivial change:

1. Inspect the relevant files.
2. Search for references to the code being changed.
3. Trace important data flow.
4. Identify related UI and logic.
5. Identify database or storage dependencies.
6. Identify navigation dependencies.
7. Identify services, permissions, APIs, or external dependencies.
8. Determine the smallest safe change.

Never make a blind change based only on a filename, class name, or assumption.

---

# 3. Preserve existing functionality

Every change must preserve unrelated functionality.

Do not accidentally:

- Remove existing features
- Break navigation
- Break buttons
- Break data loading
- Break database behavior
- Break authentication
- Break validation
- Break calculations
- Break services
- Break notifications
- Break background tasks
- Break permissions
- Replace real data with mock data
- Remove important error handling

If a requested change requires modifying existing behavior, identify the affected behavior before making the change.

---

# 4. UI changes

A UI change should remain a UI change whenever possible.

When redesigning a screen:

- Preserve business logic.
- Preserve data sources.
- Preserve validation.
- Preserve navigation.
- Preserve user actions.
- Preserve state handling.
- Preserve loading and error behavior.

If the UI requires a logic change:

1. Explain why.
2. Identify what depends on that logic.
3. Make the smallest required change.
4. Preserve the original behavior where possible.
5. Test both the changed behavior and related existing behavior.

Do not rewrite backend or business logic simply to make a UI implementation easier.

---

# 5. Adding features

New features must be integrated as additions.

Use this principle:

> Add the new feature to the existing system. Do not rebuild the existing system around the new feature unless the architecture genuinely requires it.

Before adding a feature:

- Inspect existing architecture.
- Find the correct integration point.
- Reuse existing systems where practical.
- Check related data.
- Check navigation.
- Check UI states.
- Check permissions and services.
- Check possible side effects.

After adding it:

- Test the new feature.
- Test related existing features.
- Test navigation.
- Test existing data.
- Test edge cases.
- Check logs.
- Build the project.

A new feature should feel like a natural addition to the application.

---

# 6. Do not remove functionality to solve problems

Do not delete or disable existing functionality to make a new implementation easier.

Do not remove:

- Existing screens
- Existing buttons
- Existing services
- Existing database fields
- Existing business rules
- Existing validation
- Existing user data
- Existing features

unless the user explicitly requests removal or the change has been approved.

If removing something appears necessary, explain the reason and ask for approval before making a destructive change.

---

# 7. Database and stored data safety

Treat database and persistent data changes as high-risk.

Before changing:

- Database schema
- Tables
- Columns
- Queries
- Stored values
- Migrations
- Models
- Serialization
- Persistence logic

inspect all relevant dependencies.

Never use destructive database operations as a shortcut.

Do not:

- Clear user data
- Drop tables
- Rename columns casually
- Change the meaning of stored values
- Change identifiers without migration
- Break existing installations

If a migration is necessary:

1. Explain the migration.
2. Preserve existing user data.
3. Handle old data safely.
4. Test both fresh and existing data.

---

# 8. Security

Every change must maintain or improve security.

Check for:

- Hardcoded secrets
- Sensitive information in logs
- Unsafe input handling
- Insecure storage
- Excessive permissions
- Unsafe file access
- Unsafe intents
- Insecure network behavior
- Improper authentication or authorization
- Unsafe external data handling

Never add credentials or secrets directly to source code.

Do not weaken security controls simply to make a feature easier to implement.

---

# 9. Performance

Keep changes reasonably efficient.

Avoid:

- Blocking the main thread
- Unnecessary database queries
- Repeated expensive operations
- Memory leaks
- Excessive object creation
- Unnecessary network requests
- Excessive animations
- Unnecessary background work
- Large unoptimized assets
- Rebuilding expensive UI unnecessarily

When changing startup, scrolling, database operations, background services, or large lists, consider performance before implementation.

---

# 10. Code quality

Prefer:

- Existing project conventions
- Small focused changes
- Reusable components
- Clear naming
- Simple logic
- Resource-based configuration
- Existing architecture
- Minimal duplication

Avoid:

- Unnecessary rewrites
- Unrelated refactors
- Unnecessary dependency changes
- Large architecture migrations
- Duplicate systems
- Duplicate data sources
- Duplicate state management

If unrelated technical debt is found, report it separately instead of automatically fixing it.

---

# 11. Scope control

Stay within the requested task.

If the task is:

"Redesign the profile screen"

do not automatically:

- Rewrite the database
- Rewrite authentication
- Rewrite navigation
- Upgrade dependencies
- Rename unrelated classes
- Refactor the entire project
- Change unrelated screens

If another issue is discovered, report it separately unless it directly prevents the requested task from working.

---

# 12. Change planning

For non-trivial changes, provide a short plan before editing.

Include:

## What will change

Describe the requested change.

## Files involved

List the files likely to be modified.

## Dependencies

Describe important code that depends on them.

## Risks

Identify possible regressions.

## Implementation

Describe the smallest safe approach.

## Validation

Explain how the change will be tested.

For small, low-risk changes, keep this brief.

---

# 13. Keep changes compatible

When modifying an existing function, API, class, component, or data structure:

- Check its callers.
- Check its consumers.
- Preserve expected inputs.
- Preserve expected outputs.
- Preserve important side effects.
- Preserve error behavior where appropriate.

If a method or interface must change, update every affected caller and verify the full flow.

Do not change one side of an interface while leaving the other side broken.

---

# 14. Handle dependencies carefully

Before changing a dependency, library, plugin, framework version, or build configuration:

- Check what uses it.
- Check compatibility.
- Check the build system.
- Check platform requirements.
- Check related dependencies.
- Check whether the change is necessary.

Do not upgrade dependencies simply because a newer version exists.

Do not add a library when the existing project already provides the required functionality.

---

# 15. Test after changes

A successful compilation does not prove that a change is safe.

After meaningful changes:

1. Build the project.
2. Run the affected feature.
3. Test the main user flow.
4. Test related existing behavior.
5. Check logs.
6. Check error handling.
7. Review the final diff.

For UI changes, also check:

- Layout
- Text
- Buttons
- Navigation
- Loading state
- Empty state
- Error state
- Disabled state
- Selection state
- Different screen sizes

---

# 16. Review your own changes

Before reporting a task as complete, inspect the changes.

Ask:

- Did I modify unrelated files?
- Did I accidentally delete code?
- Did I remove functionality?
- Did I change behavior unintentionally?
- Did I break a dependency?
- Did I break navigation?
- Did I break persistent data?
- Did I introduce duplicated logic?
- Did I introduce hardcoded values unnecessarily?
- Did I introduce a security issue?
- Did I introduce a performance issue?
- Did I leave debug code behind?

Correct accidental changes before finishing.

---

# 17. Git safety

Before major work:

- Check Git status.
- Check the current branch.
- Review existing uncommitted changes.
- Do not overwrite existing developer work.

After work:

- Review the diff.
- Check changed files.
- Remove unrelated changes.
- Keep the final change focused.

Never reset, discard, or overwrite developer changes without explicit permission.

---

# 18. Existing development and debug features

The project might contain:

- Debug flags
- Test data
- Development rewards
- Fast progression
- Temporary bypasses
- Mock services
- Development-only settings

Do not silently remove or change them during unrelated work.

Report them separately.

If production preparation requires changes, explain what should change and why.

---

# 19. When something is unclear

Do not guess when the change has meaningful risk.

If you are unsure:

1. Inspect more code.
2. Search for references.
3. Trace the data flow.
4. Check existing behavior.
5. Check project conventions.
6. Make the smallest safe decision.

If there is still a conflict or ambiguity, stop before making a destructive change and explain the issue.

---

# 20. Agent collaboration

Multiple AI agents might work on this project.

Do not assume another agent's changes are safe.

Before modifying existing work:

- Inspect the current state.
- Review recent changes.
- Check Git diff when available.
- Preserve intentional changes.

Do not have multiple agents editing the same files simultaneously.

If one agent implements a feature and another reviews it:

Implementation agent
→ Build
→ Test
→ Review

Reviewer
→ Inspect diff
→ Find regressions
→ Report issues

Implementation agent
→ Apply approved fixes
→ Build
→ Test again

---

# 21. Do not claim work that was not verified

Do not say:

- "It works"
- "It is secure"
- "It matches the design"
- "Nothing is broken"
- "The feature is complete"

unless you have performed an appropriate check.

Instead, report what was tested and what remains unverified.

---

# 22. Definition of done

A change is complete when:

- The requested change works.
- Existing related functionality still works.
- Existing data remains safe.
- Navigation works.
- Error handling remains reasonable.
- Security has not been weakened.
- Performance has not been unnecessarily degraded.
- The project builds successfully.
- The affected feature has been tested.
- Relevant logs have been checked.
- The final diff contains only intended changes.

---

# 23. Golden rule

Before changing:

UNDERSTAND.

While changing:

PRESERVE.

After changing:

VERIFY.

For every new feature:

INTEGRATE.

For every UI change:

KEEP THE LOGIC.

For every logic change:

KEEP THE UI AND RELATED BEHAVIOR.

For every data change:

PROTECT EXISTING DATA.

For every task:

MAKE THE SMALLEST SAFE CHANGE THAT FULLY SOLVES THE REQUEST.

# Cross-Layer Feature and UI Consistency Rule

Whenever backend, database, service, API, or business-logic work adds or changes a user-facing feature, event, state, milestone, reward, notification, or trigger, the frontend impact must be considered as part of the same change.

A backend change must not leave the frontend with an inconsistent, unfinished, or visually disconnected experience.

Before implementing a user-visible backend change:

1. Identify the frontend screen or component affected.
2. Identify how the new data or event reaches the frontend.
3. Identify the UI state that represents it.
4. Inspect the existing UI design system.
5. Reuse existing components and styles where possible.
6. If a new UI component is required, make it visually consistent with the existing product.
7. Verify loading, empty, error, disabled, selected, success, and notification states when relevant.
8. Check navigation and user flow.
9. Test the complete backend-to-frontend flow.

For DaGOAL, use the established visual language and centralized design tokens. New frontend elements must fit the existing theme rather than introduce an unrelated visual style.

The current DaGOAL core design tokens are:

- Primary: `#546B41`
- Secondary: `#99AD7A`
- Surface: `#DCCCAC`
- Light Background: `#FFF8EC`

Do not create competing brand colors or a separate visual language unless there is a documented accessibility or technical reason.

# New Feature Rule

A new feature must be treated as an addition to the existing system:

Existing system
+
New feature
=
Updated system

Do not rebuild unrelated systems to make the new feature easier to implement.

A feature is not complete when only the backend works. If users see, trigger, configure, or receive the feature through the UI, the implementation must include or clearly define its frontend integration.

# New Event Rule

When adding a new event, trigger, milestone, notification, reward, or state transition, inspect the complete flow:

Trigger
→ Business logic
→ Persistence/state
→ Event delivery
→ Frontend state
→ User-visible UI

Define the event's source, data/payload, consumers, persistence needs, UI response, and navigation impact before implementation.

Do not introduce a new event presentation that conflicts with the existing application's visual language.

Reuse existing:
- Cards
- Dialogs
- Buttons
- Colors
- Typography
- Progress indicators
- Navigation patterns
- Animations
- Notification behavior

Do not add new sounds, dialogs, notifications, overlays, or animations unless required by the feature and compatible with existing settings and UX.

# Backend-to-Frontend Compatibility

Whenever backend work changes an API, data model, business state, reward, calculation, service result, or event that the frontend consumes:

- Check every affected consumer.
- Preserve compatibility where appropriate.
- Update frontend handling when intentionally required.
- Keep data semantics consistent.
- Ensure the UI has an appropriate visual representation.
- Test the complete flow from user action or system trigger to final UI state.

Never assume that backend work is isolated from the frontend.

# Frontend-to-Backend Compatibility

Whenever frontend work introduces a new user action, input, state, filter, setting, or feature that depends on backend behavior:

- Identify the backend/data dependency first.
- Do not create fake or placeholder behavior.
- Preserve validation and error handling.
- Verify the backend supports the required state and data.
- Keep the existing API, database, and business rules intact unless an intentional change is required.

# Final Cross-Layer Check

Before completing any feature or event task, ask:

- Does the backend behavior work?
- Does the data persist correctly?
- Does the frontend receive the correct data?
- Does the UI represent the new state correctly?
- Does the new UI fit the existing theme?
- Does navigation remain correct?
- Do existing related features still work?
- Was any unrelated code changed?

If any answer is unclear, continue inspecting before reporting the task as complete.
