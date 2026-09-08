// Minimal hash router: routes are registered as `pattern -> renderFn(params, container)`.
// Pattern syntax: '/warehouse/item/:id' style segments starting with ':' are captured as params.
const routes = [];
let container = null;
let currentCleanup = null;

export function registerRoute(pattern, renderFn) {
  const segments = pattern.split('/').filter(Boolean);
  routes.push({ segments, renderFn });
}

function matchRoute(path) {
  const pathSegments = path.split('/').filter(Boolean);
  for (const route of routes) {
    if (route.segments.length !== pathSegments.length) continue;
    const params = {};
    let ok = true;
    for (let i = 0; i < route.segments.length; i++) {
      const rs = route.segments[i];
      if (rs.startsWith(':')) params[rs.slice(1)] = decodeURIComponent(pathSegments[i]);
      else if (rs !== pathSegments[i]) { ok = false; break; }
    }
    if (ok) return { renderFn: route.renderFn, params };
  }
  return null;
}

function currentPath() {
  const hash = location.hash.replace(/^#/, '') || '/pos';
  return hash.split('?')[0];
}

function updateBottomNavHighlight(path) {
  const top = path.split('/').filter(Boolean)[0] || 'pos';
  document.querySelectorAll('.bottomnav-item').forEach((a) => {
    a.classList.toggle('active', a.dataset.route === top);
  });
}

// Cart, checkout and the post-payment receipt take over the whole screen — no topbar, no bottom
// nav — same as the design's isCartView/isCheckoutView/isReceiptView overlays.
const CHROMELESS_PREFIXES = ['/cart', '/checkout'];
function updateChrome(path) {
  const hideChrome = CHROMELESS_PREFIXES.some((p) => path === p || path.startsWith(p + '/'));
  document.body.classList.toggle('chrome-hidden', hideChrome);
}

async function render() {
  const path = currentPath();
  const match = matchRoute(path);
  if (typeof currentCleanup === 'function') { try { currentCleanup(); } catch (e) { /* ignore */ } }
  currentCleanup = null;
  updateBottomNavHighlight(path);
  updateChrome(path);
  container.scrollTop = 0;
  if (!match) {
    container.innerHTML = '<div class="empty-state">Страница не найдена</div>';
    return;
  }
  const result = await match.renderFn(match.params, container);
  if (typeof result === 'function') currentCleanup = result;
}

export function navigate(path) {
  location.hash = `#${path}`;
}

export function startRouter(appContainer) {
  container = appContainer;
  window.addEventListener('hashchange', render);
  render();
}
