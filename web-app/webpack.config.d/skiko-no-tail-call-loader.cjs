/**
 * WebKit can corrupt float arguments on JavaScript to WebAssembly tail calls
 * (WebKit bug 329421 / JetBrains CMP-10986). Every Skiko export wrapper is a
 * pure tail call of the form:
 *
 *   export let name = (...a) => (name = loadedWasm._["name"])(...a)
 *
 * On affected Safari / iOS versions that leaves a white canvas after startup
 * (loading splash clears, no console error). A call inside `try` is never a
 * tail call, so the floats stay intact.
 *
 * Remove this loader once the minimum supported Safari/iOS no longer needs it.
 */
module.exports = function skikoNoTailCallLoader(source) {
  this.cacheable && this.cacheable();
  return source.replace(
    /export let (\w+) = \(\.\.\.a\) => \(\1 = loadedWasm\._\["\1"\]\)\(\.\.\.a\)/g,
    'export let $1 = (...a) => { try { return ($1 = loadedWasm._["$1"])(...a); } catch (e) { throw e; } }'
  );
};
