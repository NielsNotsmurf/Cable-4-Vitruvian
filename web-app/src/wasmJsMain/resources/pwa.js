// Runs before the app: offline support, and Android's install prompt, which only fires early.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('sw.js').catch(() => {}));
}
window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); window.__installPrompt = e; });
window.addEventListener('appinstalled', () => { window.__installPrompt = null; });

// Browsers without WebAssembly GC (iOS before 18.2, also in Bluefy) can't run the app: say so
// instead of leaving a white page. Errors before the first screen show up the same way.
function showStartProblem(text) {
  let e = document.getElementById('loading');
  if (!e) { e = document.createElement('div'); e.id = 'loading'; document.body.appendChild(e); }
  e.style.cssText += ';padding:24px;text-align:center;line-height:1.5';
  e.textContent = text;
}
const wasmGc = typeof WebAssembly === 'object' &&
  WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 127, 0]));
window.__startProblem = wasmGc ? null :
  'This browser is too old for Cable. Update iOS to 18.2 or newer (or your browser), then open this page again.';
window.addEventListener('DOMContentLoaded', () => { if (window.__startProblem) showStartProblem(window.__startProblem); });
window.addEventListener('error', e => { if (!window.__started) showStartProblem('Cable could not start: ' + (e.message || 'unknown error')); });
window.addEventListener('unhandledrejection', e => { if (!window.__started) showStartProblem('Cable could not start: ' + (e.reason && e.reason.message || e.reason)); });
