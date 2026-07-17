/* ============================================================
   NEXUS AI — File Manager Extended
   ============================================================ */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  initBreadcrumb();
  initSortSelect();
});

function initBreadcrumb() {
  document.querySelectorAll('.bc-item').forEach(item => {
    item.addEventListener('click', () => {
      document.querySelectorAll('.bc-item').forEach(b => b.classList.remove('active'));
      item.classList.add('active');
      // Could navigate filesystem here
    });
  });
}

function initSortSelect() {
  document.getElementById('sortSelect')?.addEventListener('change', e => {
    showToast(`Sorted by: ${e.target.value}`);
  });
}
