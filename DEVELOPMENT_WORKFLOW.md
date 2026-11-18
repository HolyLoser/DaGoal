# General AI Development Workflow

## Purpose

This workflow defines how AI coding agents should work on the entire project.

It applies to:

- Frontend
- Backend
- Database
- APIs
- Android
- Web
- Services
- Infrastructure
- Full-stack features
- UI/UX implementation
- Bug fixes
- Refactoring
- Performance work
- Security work

It is agent-neutral.

It applies to Claude, Gemini, Codex, Copilot, Cursor, Windsurf, and other AI coding agents.

The project-level rules in `AGENTS.md` always apply.

---

# 1. Core Development Principle

The existing project is a working system.

Do not treat it as a blank project.

Every task follows:

UNDERSTAND
→ PLAN
→ IMPLEMENT
→ VERIFY
→ REVIEW
→ HANDOFF

The goal is not to produce the most code.

The goal is to make the smallest safe change that fully solves the requested problem.

---

# 2. Agent Roles

An agent's role is determined by the task, not by the AI tool being used.

An agent might act as:

- Analyst
- Frontend developer
- Backend developer
- Full-stack developer
- Database developer
- Android developer
- Reviewer
- Tester
- Debugger
- Security reviewer
- Performance reviewer

Do not assume that one AI tool is permanently assigned to one role.

The task specification determines the role.

---

# 3. Phase 1: Understand

Before changing code, inspect the existing system.

Determine:

- What the requested feature or problem is.
- Where the current behavior is implemented.
- Which files are involved.
- Which components depend on them.
- What data enters the system.
- What data leaves the system.
- Which database or persistent storage is involved.
- Which APIs or services are involved.
- Which UI screens are affected.
- Which existing features interact with the change.

Search the codebase before editing.

Do not make assumptions based only on filenames.

---

# 4. Phase 2: Define Scope

Every task must have a clear scope.

## Required

State:

- What is changing.
- Why it is changing.
- What must remain unchanged.
- Which areas are allowed to change.
- Which areas should not change unless required.

Example:

### Task

Redesign the Quest screen.

### Allowed

- Quest layout
- Quest UI resources
- Required UI integration

### Conditional

- Java/Kotlin logic only if required for UI integration

### Protected

- Quest generation
- XP calculations
- Reward calculations
- Database schema
- Existing navigation

The scope prevents unrelated changes.

---

# 5. Phase 3: Impact Analysis

Before implementation, determine the impact.

Check:

## Frontend impact

- Screens
- Components
- Navigation
- State
- Forms
- Validation
- Data binding
- Loading states
- Error states

## Backend impact

- Business logic
- APIs
- Services
- Authentication
- Validation
- Calculations
- Background processes

## Database impact

- Models
- Tables
- Fields
- Queries
- Migrations
- Existing user data

## Platform impact

- Permissions
- Services
- Notifications
- Storage
- Build configuration
- Dependencies

## Security impact

- Authentication
- Authorization
- User input
- Sensitive data
- Storage
- Logs
- External communication

## Performance impact

- Startup
- Database operations
- Network requests
- Memory
- Rendering
- Background work

---

# 6. Phase 4: Create the Implementation Plan

For a meaningful task, create a short implementation plan.

Use:

## Objective

What must be achieved?

## Existing behavior

What currently works and must remain?

## Files to inspect

Which files need investigation?

## Files likely to change

Which files are expected to change?

## Protected areas

Which files or systems should remain untouched?

## Dependencies

What other systems are affected?

## Implementation

What is the smallest safe implementation?

## Testing

How will the result be verified?

If investigation shows that the original plan is unsafe, revise the plan before editing.

---

# 7. Phase 5: Implement

Implement the smallest safe change.

Rules:

- Reuse existing architecture.
- Reuse existing components.
- Reuse existing data.
- Preserve existing interfaces when possible.
- Avoid unrelated refactoring.
- Avoid unnecessary dependency changes.
- Avoid duplicate systems.
- Avoid rewriting working logic.
- Avoid changing unrelated files.

If a larger change is genuinely required, explain why.

---

# 8. Frontend Development

For frontend tasks:

## Preserve

- Existing interactions
- Navigation
- Data sources
- Validation
- State management
- Business logic
- API behavior
- Loading behavior
- Error behavior

## UI redesign

When implementing a design from Figma or another reference:

- Use the reference as the visual source.
- Preserve the application's real data.
- Preserve existing behavior.
- Build reusable components.
- Use shared colors and dimensions.
- Handle loading, empty, error, disabled, and selected states.
- Support reasonable screen sizes.
- Do not replace working logic with mock behavior.

A visual redesign should not become an accidental backend rewrite.

---

# 9. Backend Development

For backend tasks:

## Preserve

- Existing API contracts unless intentionally changed.
- Existing data semantics.
- Existing authentication.
- Existing authorization.
- Existing validation.
- Existing business rules.

Before changing an API:

1. Find all consumers.
2. Determine the impact.
3. Update consumers when intentionally required.
4. Preserve backward compatibility where appropriate.
5. Test the complete flow.

Do not change a backend response structure without checking the frontend or other clients that consume it.

---

# 10. Database Development

Treat database changes as high-risk.

Before changing the database:

- Inspect current schema.
- Inspect queries.
- Inspect models.
- Inspect migrations.
- Inspect existing data assumptions.
- Find all consumers.

Never destroy existing data as a shortcut.

If a schema change is required:

- Explain it.
- Provide a safe migration.
- Preserve existing data.
- Test fresh installation.
- Test existing installation.

---

# 11. Cross-Layer Features

When a feature affects multiple layers, treat the whole flow as one system.

Example:

User action
→ UI
→ API/business logic
→ database
→ response
→ UI state

Check every layer.

Do not modify only one layer and assume the rest will work.

For a new feature, document:

- Frontend changes
- Backend changes
- Database changes
- API changes
- Security changes
- Testing requirements

---

# 12. Safe Change Rules

Before changing an existing method, class, component, API, or data structure:

1. Find its callers.
2. Find its consumers.
3. Understand its inputs.
4. Understand its outputs.
5. Understand important side effects.
6. Check error behavior.
7. Check persistent data behavior.

Then make the smallest required modification.

---

# 13. New Feature Rules

A new feature should be additive.

Preferred:

Existing System
+
New Feature
=
Updated System

Avoid:

Existing System
→ Rewrite
→ New Feature
→ Unrelated regressions

Reuse existing systems where appropriate.

Do not create a second database system, state system, authentication system, navigation system, or service when an existing one already serves the purpose.

---

# 14. Testing

Testing must cover more than compilation.

## Minimum validation

- Build
- Main feature flow
- Related existing feature
- Error handling
- Navigation
- Data persistence
- Logs

## Frontend

Check:

- Layout
- Text
- Touch targets
- Scrolling
- Loading
- Empty states
- Error states
- Disabled states
- Selection states
- Different screen sizes

## Backend

Check:

- Valid input
- Invalid input
- Authentication
- Authorization
- Error responses
- Data persistence
- Existing API consumers

## Cross-layer

Check:

UI
→ backend
→ database
→ backend
→ UI

---

# 15. Regression Testing

After implementation, test the features that interact with the changed area.

Example:

If Shop changes:

Shop
→ Purchase
→ Currency
→ Ownership
→ Wardrobe
→ Avatar

If Quest changes:

Quest
→ Completion
→ XP
→ Rewards
→ Progress
→ Database

If Profile changes:

Profile
→ User data
→ Avatar
→ Streak
→ Achievements

Do not test only the screen that was edited.

---

# 16. Review

After implementation, review the final diff.

Check:

- Intended files only
- No accidental deletion
- No unrelated refactoring
- No debug code
- No mock data replacing real data
- No broken imports
- No unnecessary dependencies
- No security regressions
- No performance regressions
- No database damage
- No API contract damage

---

# 17. Agent Handoff

When one agent finishes work for another agent, provide a handoff.

Use:

## Task completed

Describe what was implemented.

## Files changed

List changed files.

## Files intentionally untouched

List protected areas.

## Logic changes

State exactly what changed.

## Database changes

State whether the database changed.

## API changes

State whether API contracts changed.

## UI changes

Describe the UI changes.

## Testing performed

List tests performed.

## Known issues

List unresolved issues.

## Next agent actions

Tell the next agent what needs verification or continuation.

---

# 18. Reviewer Workflow

A reviewing agent must not automatically rewrite the implementation.

The reviewer should:

1. Read the task specification.
2. Read `AGENTS.md`.
3. Inspect the diff.
4. Check architecture.
5. Check functionality.
6. Check security.
7. Check performance.
8. Check regression risks.
9. Report issues.
10. Suggest the smallest safe fix.

Only modify code when the review task explicitly authorizes fixes.

---

# 19. Conflict Handling

If the task conflicts with existing behavior:

Do not silently choose.

Determine:

- What currently happens.
- What the new requirement expects.
- Which other systems depend on the current behavior.
- What would break if changed.

Then report the conflict.

For high-risk conflicts, wait for approval before making a destructive change.

---

# 20. Git Workflow

Before implementation:

- Check Git status.
- Check the current branch.
- Review uncommitted work.

During implementation:

- Keep changes focused.
- Avoid overwriting unrelated work.

After implementation:

- Review the diff.
- Check changed files.
- Remove accidental changes.

Never reset or discard developer work without explicit permission.

---

# 21. Task Completion Criteria

A task is complete only when:

- The requested behavior works.
- Existing related behavior still works.
- Data remains safe.
- UI and backend remain compatible.
- Navigation remains functional.
- Security remains intact.
- Performance remains reasonable.
- The project builds.
- Relevant tests pass.
- The final diff is clean.
- Known limitations are documented.

---

# 22. Standard Task File Template

When creating a task-specific Markdown file, use this structure:

# [Task Name]

## Objective

Describe the desired result.

## Context

Explain why the change is needed.

## Reference

Provide Figma, screenshots, documentation, API references, or other relevant material.

## Existing Behavior

Describe functionality that must remain unchanged.

## Scope

Describe what is allowed to change.

## Protected Areas

Describe what must not change unless technically required.

## Requirements

List functional and visual requirements.

## Frontend Requirements

List UI-related requirements.

## Backend Requirements

List business logic, API, or service requirements.

## Database Requirements

List persistence requirements.

## Security Requirements

List security considerations.

## Performance Requirements

List performance considerations.

## Implementation Guidance

Describe the preferred approach without forcing unnecessary architecture changes.

## Files to Inspect

List known files or directories.

## Files Likely to Change

List expected changes.

## Testing

List required tests.

## Acceptance Criteria

Define what must be true for the task to be complete.

## Handoff

Describe what the next agent should verify or continue.

---

# 23. Final Principle

The best AI coding agent is not the one that changes the most code.

It is the one that understands the existing system, makes the smallest correct change, protects everything unrelated, and proves that the result works.

UNDERSTAND
→ PLAN
→ CHANGE
→ VERIFY
→ REVIEW
→ HANDOFF

Never skip understanding.

Never skip verification.

# Cross-Layer Feature, Event, and UI Theme Requirement

For any task that adds or changes a feature, event, backend behavior, data state, reward, milestone, notification, or service that is visible to users, treat frontend and backend as one connected product flow.

A backend feature must include a frontend impact check.

A frontend feature must include a backend/data impact check when applicable.

## When Backend Adds a Feature or Event

The agent must inspect:

- Which frontend screens consume it
- Which UI states represent it
- What data/payload is delivered
- Whether persistence is required
- Whether navigation changes
- Whether notifications, dialogs, sounds, overlays, or animations are involved
- Whether existing UI components already support the new behavior

The new frontend behavior must fit the existing design system.

Do not create an unrelated visual language for a backend-created feature or event.

## When Adding a New Feature

Use:

Existing system
+
New feature
=
Updated system

The feature must fit the existing:

- Colors
- Typography
- Cards
- Buttons
- Navigation
- Icons
- Progress indicators
- Dialogs
- Loading states
- Empty states
- Error states
- Success states
- Animation style

For DaGOAL, use the official design tokens:

- Primary: `#546B41`
- Secondary: `#99AD7A`
- Surface: `#DCCCAC`
- Light Background: `#FFF8EC`

Reuse centralized resources instead of creating new competing colors.

## When Adding a New Event

Trace:

Trigger
→ Business logic
→ Persistence/state
→ Event delivery
→ Frontend state
→ User-visible response

Document:

- Event name
- Trigger
- Source
- Payload/data
- Consumer
- Persistence requirement
- UI treatment
- Navigation impact
- Notification/sound requirements
- Security impact
- Performance impact

If the event is user-visible, its presentation must look like a natural part of the existing product.

## Backend-to-Frontend Contract

A backend change is incomplete when it creates user-visible data or state without confirming how the frontend consumes and presents it.

Before changing an API, model, response, business state, reward, or event:

1. Find all consumers.
2. Determine the UI impact.
3. Preserve compatibility where appropriate.
4. Update consumers intentionally.
5. Verify the complete user flow.

## Frontend-to-Backend Contract

A frontend change is incomplete when it introduces a new action or state that depends on backend behavior without checking the backend/data flow.

Do not use mock behavior to hide a missing backend implementation.

## Cross-Layer Acceptance

A feature or event is complete only when:

- Backend logic works.
- Required data persists correctly.
- Frontend receives the expected data/state.
- UI represents the state correctly.
- UI matches the existing theme.
- Navigation remains correct.
- Existing related features still work.
- No unrelated systems were changed.
