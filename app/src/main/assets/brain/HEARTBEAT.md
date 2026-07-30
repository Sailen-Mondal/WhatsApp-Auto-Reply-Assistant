# HEARTBEAT — Proactive Behavior Checklist

This file defines any proactive or periodic checks OWL performs outside of direct message responses.

For a primarily reactive agent like this one (WhatsApp auto-reply), most behavior is triggered by incoming messages. This file is kept minimal but available for future extension.

---

## Current Proactive Behaviors

### Memory Review (Triggered after 10+ auto-replies in a day)
- Review today's memory/YYYY-MM-DD.md
- Check if any temporary notes should be promoted to MEMORY.md
- Remove entries from today's file that turned out to be one-time anomalies

### Quality Self-Check (Optional, on demand)
- Review the last 5 auto-replies in the LLM debug log
- Check if any reply sounded templated or robotic
- If a pattern is found, note it in today's memory file

### Session Startup Check
- Confirm MEMORY.md is not blank (if blank, re-seed from BOOTSTRAP.md)
- Confirm today's memory/YYYY-MM-DD.md file exists (create if missing)
- Confirm SOUL.md and IDENTITY.md are loaded

---

## Scheduled Tasks

Currently none. Future additions can be placed here if proactive behaviors are needed (e.g., daily summary, auto-archival of old memory files).

---

## Notes

If this agent becomes more autonomous in the future, this file is where to define what it does unprompted. For now, keep it simple and don't add complexity that isn't needed.
