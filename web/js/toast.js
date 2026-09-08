export function showToast(message, ms = 2600) {
  const root = document.getElementById('toast-root');
  const node = document.createElement('div');
  node.className = 'toast';
  node.textContent = message;
  root.appendChild(node);
  setTimeout(() => node.remove(), ms);
}
