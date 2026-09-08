import { seedDefaultCategoriesIfEmpty } from './db.js';
import { loadSettings, onSettingsChange } from './state.js';
import { startRouter } from './router.js';
import './screens/pos.js';
import './screens/checkout.js';
import './screens/receipts.js';
import './screens/warehouse.js';
import './screens/purchase.js';
import './screens/catalog.js';
import './screens/stats.js';
import './screens/settings.js';

function applyTheme(settings) {
  const root = document.documentElement;
  root.dataset.theme = settings.themeMode === 'DARK' ? 'dark' : settings.themeMode === 'LIGHT' ? 'light' : '';
  root.style.setProperty('--color-primary', '#' + settings.accentColorHex.replace(/^FF/i, ''));
  root.style.fontSize = `${Math.round(16 * (settings.textScale || 1))}px`;
}

async function bootstrap() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./service-worker.js').catch((e) => console.warn('SW register failed', e));
  }

  await seedDefaultCategoriesIfEmpty();
  const settings = await loadSettings();
  applyTheme(settings);
  onSettingsChange(applyTheme);

  const appContent = document.getElementById('app-content');
  startRouter(appContent);
}

bootstrap();
