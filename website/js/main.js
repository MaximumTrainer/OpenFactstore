/* OpenFactstore Website — main.js */

(function () {
  'use strict';

  // ── Active nav link ─────────────────────────────────────
  function setActiveNav() {
    const page = location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('nav a').forEach(function (link) {
      const href = link.getAttribute('href');
      if (href === page || (page === '' && href === 'index.html')) {
        link.classList.add('active');
      }
    });
  }

  // ── Mobile sidebar toggle ────────────────────────────────
  function initMobileNav() {
    const toggle = document.querySelector('.nav-toggle');
    const sidebar = document.querySelector('.sidebar');
    if (!toggle || !sidebar) return;

    toggle.addEventListener('click', function () {
      const open = sidebar.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));
      toggle.textContent = open ? '✕' : '☰';
    });

    // Close sidebar when a nav link is clicked on mobile
    sidebar.querySelectorAll('a').forEach(function (link) {
      link.addEventListener('click', function () {
        sidebar.classList.remove('open');
        toggle.textContent = '☰';
        toggle.setAttribute('aria-expanded', 'false');
      });
    });

    // Close sidebar when clicking outside
    document.addEventListener('click', function (e) {
      if (!sidebar.contains(e.target) && !toggle.contains(e.target)) {
        sidebar.classList.remove('open');
        toggle.textContent = '☰';
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  }

  // ── Smooth scroll for anchor links ──────────────────────
  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(function (anchor) {
      anchor.addEventListener('click', function (e) {
        const id = this.getAttribute('href').slice(1);
        const target = document.getElementById(id);
        if (target) {
          e.preventDefault();
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      });
    });
  }

  // ── Copy code button ─────────────────────────────────────
  function initCopyButtons() {
    document.querySelectorAll('pre').forEach(function (block) {
      const btn = document.createElement('button');
      btn.className = 'copy-btn';
      btn.textContent = 'Copy';
      btn.style.cssText = [
        'position:absolute', 'top:8px', 'right:8px',
        'background:#30363d', 'color:#c9d1d9', 'border:1px solid #444c56',
        'border-radius:4px', 'padding:3px 10px', 'font-size:12px',
        'cursor:pointer', 'font-family:inherit', 'transition:background 0.15s'
      ].join(';');

      btn.addEventListener('click', function () {
        const text = block.querySelector('code') ? block.querySelector('code').innerText : block.innerText;
        navigator.clipboard.writeText(text).then(function () {
          btn.textContent = 'Copied!';
          btn.style.background = '#1a7f37';
          btn.style.borderColor = '#2ea043';
          setTimeout(function () {
            btn.textContent = 'Copy';
            btn.style.background = '#30363d';
            btn.style.borderColor = '#444c56';
          }, 2000);
        });
      });

      block.style.position = 'relative';
      block.appendChild(btn);
    });
  }

  // ── Init ─────────────────────────────────────────────────
  document.addEventListener('DOMContentLoaded', function () {
    setActiveNav();
    initMobileNav();
    initSmoothScroll();
    initCopyButtons();
  });
}());
