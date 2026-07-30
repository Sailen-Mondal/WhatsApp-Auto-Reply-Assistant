# BOOTSTRAP — First-Run Setup

This file defines what happens when this workspace is first created or reset. It is the startup ritual. After bootstrapping is complete, this file stays but is no longer actively used in every session.

---

## Purpose

Bootstrap ensures the agent starts with a strong, intentional identity from day one instead of discovering it slowly through trial and error.

---

## First-Run Sequence

When the brain files are first copied to device storage:

1. **Verify all files exist:**
   - SOUL.md, IDENTITY.md, AGENTS.md, USER.md, TOOLS.md, MEMORY.md, HEARTBEAT.md
   - skills/whatsapp-replies/SKILL.md
   - memory/ directory (create if missing)

2. **Create today's memory file:**
   - Create `memory/YYYY-MM-DD.md` with the current date
   - Add a single line: `# First session. Brain initialized from defaults.`

3. **Confirm identity is loaded:**
   - OWL should be able to state its name, role, and values immediately
   - No need for a "hello" prompt — the identity is pre-loaded

4. **Pre-seed MEMORY.md:**
   - The default MEMORY.md already contains the core preferences from the persona specification
   - These are the stable starting facts. They can be updated as real preferences emerge.

---

## Initial Memory Seed (Reference)

These facts are pre-loaded in MEMORY.md on first run:

- User prefers Banglish by default (Kolkata dialect)
- User dislikes overly polished or corporate-sounding replies
- User values honesty over flattery
- User wants subtle flirtation only when appropriate
- User appreciates wit when it is earned, not forced
- User prefers replies that feel human and unforced
- User is practical, direct, and growth-focused

---

## Reset Instructions

If the user wants to reset OWL to factory defaults:
1. Delete all files in `filesDir/brain/`
2. The app will automatically re-copy from `assets/brain/` on next launch
3. All customizations to brain files will be lost — export first if needed

---

## Import from Old Persona

If migrating from the old hardcoded persona (PromptTemplates.kt era):
- The default MEMORY.md and SOUL.md already contain the equivalent content
- No manual migration needed
- The old `systemPrompt()` function is now replaced by BrainLoader

---

## Status Tracking

- [ ] Brain files initialized
- [ ] MEMORY.md seeded
- [ ] Today's memory file created
- [ ] First reply generated successfully
