// Run with mcp__claude-in-chrome__javascript_tool on https://claude.ai/new#settings/usage.
// Waits for the settings dialog, then returns {percent, resets, relative} as JSON, or null.
// Reload the tab first (navigate to the same URL) so the numbers are fresh.
await new Promise(r => setTimeout(r, 2500));
const text = (document.querySelector('[role=dialog]') || document.body).innerText;
const block = text.match(/Current session\s*(Resets[^\n]*)\s*(\d+)%\s*used/i);
block
  ? JSON.stringify({
      percent: Number(block[2]),
      resets: block[1].replace(/^Resets\s*(at|in)?\s*/i, '').trim(),
      relative: /Resets\s+in/i.test(block[1]),
    })
  : null;
