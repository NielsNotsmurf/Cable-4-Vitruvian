// Runs before the app: offline support, and Android's install prompt, which only fires early.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('sw.js').catch(() => {}));
}
window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); window.__installPrompt = e; });
window.addEventListener('appinstalled', () => { window.__installPrompt = null; });
